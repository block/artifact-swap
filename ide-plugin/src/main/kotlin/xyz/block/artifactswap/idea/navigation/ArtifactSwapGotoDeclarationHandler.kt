package xyz.block.artifactswap.idea.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import xyz.block.artifactswap.idea.gradle.AarPackageCacheService
import xyz.block.artifactswap.idea.settings.ArtifactSwapSettings
import xyz.block.artifactswap.idea.util.AndroidPluginSupport
import xyz.block.artifactswap.idea.util.AndroidResourceHelper
import xyz.block.artifactswap.idea.util.SourceFileFinder
import xyz.block.artifactswap.idea.util.SourceSetUtils
import xyz.block.artifactswap.idea.util.artifactSwapModel
import xyz.block.artifactswap.idea.util.findCorrespondingElement
import xyz.block.artifactswap.idea.util.resolveAllReferences
import xyz.block.artifactswap.model.ArtifactSwapModel
import xyz.block.artifactswap.model.artifactIdToProjectPath
import xyz.block.artifactswap.model.extractArtifactId
import xyz.block.artifactswap.model.isSwappedArtifactPath
import xyz.block.artifactswap.model.projectPathToDirectory

/**
 * A [GotoDeclarationHandler] that intercepts navigation to classes inside swapped artifact JARs and
 * redirects to the actual source files in the project.
 *
 * This handler is registered with `order="first"` so it has a chance to intercept navigation before
 * the default handlers open the decompiled class file from the JAR.
 */
class ArtifactSwapGotoDeclarationHandler : GotoDeclarationHandler {

  private val logger = Logger.getInstance(ArtifactSwapGotoDeclarationHandler::class.java)

  override fun getGotoDeclarationTargets(
    sourceElement: PsiElement?,
    offset: Int,
    editor: Editor?,
  ): Array<PsiElement>? {
    if (sourceElement == null) {
      return null
    }

    val project = sourceElement.project

    // Don't run during indexing - defer to default navigation
    if (DumbService.isDumb(project)) {
      return null
    }

    // If there is no model, artifactswap is not applied and we should not do anything
    val model = project.artifactSwapModel ?: return null

    val sourceFile = sourceElement.containingFile?.virtualFile
    // Get ALL resolved reference targets (handles multi-resolve for resources with variants)
    val resolvedElements = sourceElement.resolveAllReferences()

    if (resolvedElements.isEmpty()) {
      // Even if we can't resolve, check if we're navigating FROM a swapped artifact
      // If so, we might be able to open the entire source file
      if (sourceFile != null && model.isSwappedArtifactPath(sourceFile.path)) {
        val result =
          openSourceFileForCurrentLocation(project, sourceFile.path, sourceElement, model)
        if (result != null) {
          return result
        }
      }
      return null
    }

    // Redirect each target from binary to source
    val redirectedTargets =
      resolvedElements.flatMap { resolvedElement ->
        ProgressManager.checkCanceled()
        val result = redirectToSourceIfNeeded(project, resolvedElement, model)
        result?.toList() ?: emptyList()
      }

    return if (redirectedTargets.isNotEmpty()) {
      redirectedTargets.toTypedArray()
    } else {
      null
    }
  }

  /**
   * Opens the source file when we're already viewing a swapped artifact. This is a fallback when
   * reference resolution fails but we want to navigate within the same file.
   */
  private fun openSourceFileForCurrentLocation(
    project: Project,
    artifactFilePath: String,
    sourceElement: PsiElement,
    model: ArtifactSwapModel,
  ): Array<PsiElement>? {
    // Check user's preference from settings (no dialog during navigation)
    val settings = ArtifactSwapSettings.getInstance()
    val shouldNavigateToSource =
      settings.navigationBehavior == ArtifactSwapSettings.NavigationBehavior.JUMP_TO_SOURCE

    if (!shouldNavigateToSource) {
      return null
    }

    ProgressManager.checkCanceled()

    // Find and open the source file (potentially expensive I/O operation)
    val sourceFile =
      SourceFileFinder.findSourceFile(project, artifactFilePath, sourceElement, model)
    if (sourceFile == null) {
      logger.warn("Could not find source file for swapped artifact: $artifactFilePath")
      return null
    }

    val psiSourceFile = PsiManager.getInstance(project).findFile(sourceFile)
    if (psiSourceFile == null) {
      logger.error("Could not get PSI for source file: ${sourceFile.path}")
      return null
    }

    return arrayOf(psiSourceFile)
  }

  /**
   * Attempts to redirect navigation from a binary artifact element to its source equivalent.
   *
   * @param project The current project
   * @param targetElement The element that navigation would go to (potentially in a JAR)
   * @param model The Artifact Swap model
   * @return An array containing the source element to navigate to, or null to use default behavior
   */
  private fun redirectToSourceIfNeeded(
    project: Project,
    targetElement: PsiElement,
    model: ArtifactSwapModel,
  ): Array<PsiElement>? {
    // Special handling for Android light fields (synthetic R fields from Android plugin)
    if (targetElement is PsiField && AndroidPluginSupport.isAndroidLightField(targetElement)) {
      return handleAndroidLightField(project, targetElement, model)
    }

    val targetFile = targetElement.containingFile?.virtualFile ?: return null
    val targetFilePath = targetFile.path

    // Check if this is a file inside a swapped artifact JAR
    if (!model.isSwappedArtifactPath(targetFilePath)) {
      return null
    }

    // Check user's preference from settings (no dialog during navigation)
    val settings = ArtifactSwapSettings.getInstance()
    val shouldNavigateToSource =
      settings.navigationBehavior == ArtifactSwapSettings.NavigationBehavior.JUMP_TO_SOURCE

    if (!shouldNavigateToSource) {
      // User prefers binary, let default behavior handle it
      return null
    }

    // Extract artifact info
    val artifactId = model.extractArtifactId(targetFilePath)

    if (artifactId == null) {
      logger.warn("Could not extract artifact ID from path: $targetFilePath")
      return null
    }

    ProgressManager.checkCanceled()

    // Find the corresponding source file
    val sourceFile = SourceFileFinder.findSourceFile(project, targetFilePath, targetElement, model)
    if (sourceFile == null) {
      logger.warn("Could not find source file for: $targetFilePath")
      return null
    }

    ProgressManager.checkCanceled()

    // Open the source file and find the corresponding element
    val psiSourceFile = PsiManager.getInstance(project).findFile(sourceFile)
    if (psiSourceFile == null) {
      logger.warn("Could not get PSI for source file: ${sourceFile.path}")
      return null
    }

    // Try to find the specific symbol in the source file
    val sourceElement = targetElement.findCorrespondingElement(psiSourceFile)
    if (sourceElement != null) {
      return arrayOf(sourceElement)
    }

    // Fall back to navigating to the file itself
    return arrayOf(psiSourceFile)
  }

  /**
   * Handles Android light fields (synthetic R fields like R.string.close, R.styleable.MyView_attr).
   * These don't have a containing file, so we extract resource info using reflection.
   */
  private fun handleAndroidLightField(
    project: Project,
    resourceField: PsiElement,
    model: ArtifactSwapModel,
  ): Array<PsiElement>? {
    if (resourceField !is PsiField) {
      return null
    }

    // Extract resource info using helper (tries Android plugin classes, falls back to reflection)
    val (resourceName, resourceType) =
      AndroidResourceHelper.extractResourceInfo(resourceField) ?: return null

    // Get the outer R class (containingClass is R.string, we need R)
    val rClass = resourceField.containingClass?.containingClass ?: return null

    ProgressManager.checkCanceled()

    // Try to get the artifact ID from the containing file path
    // For synthetic light fields, the containing file might be null, so we need to
    // find the R class JAR through the project's libraries
    val containingFile = rClass.containingFile?.virtualFile
    val artifactId =
      if (containingFile != null) {
        model.extractArtifactId(containingFile.path)
      } else {
        findArtifactIdForRClass(project, rClass)
      }

    // If we couldn't extract artifact ID, we can't proceed
    if (artifactId == null) {
      logger.info("Unable to extract artifact ID for $resourceType $resourceName")
      return null
    }

    val projectPath = model.artifactIdToProjectPath(artifactId)
    val moduleDir = model.projectPathToDirectory(projectPath)
    val basePath = project.basePath ?: return null

    // Check if the Gradle module is loaded in IntelliJ
    // If it's loaded, the module is NOT swapped - let Android plugin handle navigation
    val moduleManager = ModuleManager.getInstance(project)
    val allModules = moduleManager.modules

    // Detect project prefix from root module (e.g., "root-project-name" from
    // "root-project-name.profile")
    val rootModule = allModules.firstOrNull { it.name == project.name }
    val projectPrefix = rootModule?.name ?: project.name

    // Build module name: IntelliJ uses "projectname.path.to.module" format
    val modulePathPart = projectPath.removePrefix(":").replace(":", ".")
    val possibleModuleNames =
      setOf(
        "$projectPrefix.$modulePathPart", // "root-project-name.profile.views" (with project prefix)
        modulePathPart, // "profile.views" (without prefix, for simpler projects)
      )

    val isModuleLoaded = allModules.any { module -> module.name in possibleModuleNames }

    if (isModuleLoaded) {
      // Module is loaded in IDE - it's not swapped, let Android plugin handle it
      return null
    }

    ProgressManager.checkCanceled()

    // Module is not loaded in IDE - it's swapped, we handle navigation
    // Special handling for ManifestLightField - navigate to AndroidManifest.xml
    if (AndroidPluginSupport.isManifestLightField(resourceField)) {
      return findAndroidManifest(project, basePath, moduleDir)
    }

    // Find the resource file
    val sourceFile =
      AndroidResourceHelper.findResourceFileByType(basePath, moduleDir, resourceType, resourceName)
        ?: return null

    // Parse the XML file
    val psiSourceFile = PsiManager.getInstance(project).findFile(sourceFile) ?: return null
    if (psiSourceFile !is XmlFile) {
      return arrayOf(psiSourceFile)
    }

    // Find the specific XML tag with matching name attribute
    val allTags = PsiTreeUtil.findChildrenOfType(psiSourceFile, XmlTag::class.java)
    val matchingTag = allTags.find { tag -> tag.getAttributeValue("name") == resourceName }

    return if (matchingTag != null) {
      arrayOf(matchingTag)
    } else {
      arrayOf(psiSourceFile)
    }
  }

  /**
   * Finds the artifact ID for an R class by looking up the package name in the cache. Used for
   * synthetic Android light field elements.
   *
   * The cache is populated by the background AAR scan after Gradle sync, so this is just a lookup.
   * If not found, it means the AAR doesn't exist or doesn't have a valid manifest.
   */
  private fun findArtifactIdForRClass(project: Project, rClass: PsiClass): String? {
    val qualifiedName = rClass.qualifiedName ?: return null
    val packageName = qualifiedName.substringBeforeLast(".R")

    val cacheService = AarPackageCacheService.getInstance(project)

    // Lookup in cache - the background scan should have already populated this
    return cacheService.getArtifactForPackage(packageName)
  }

  /** Finds AndroidManifest.xml in the module. Checks src/main first, then other source sets. */
  private fun findAndroidManifest(
    project: Project,
    basePath: String,
    moduleDir: String,
  ): Array<PsiElement>? {
    val moduleRoot =
      LocalFileSystem.getInstance().findFileByPath("$basePath/$moduleDir") ?: return null
    val srcDir = moduleRoot.findChild("src") ?: return null
    val psiManager = PsiManager.getInstance(project)

    return SourceSetUtils.getSourceSets(srcDir)
      .asSequence()
      .mapNotNull { it.findChild("AndroidManifest.xml") }
      .mapNotNull { psiManager.findFile(it) }
      .firstOrNull()
      ?.let { arrayOf(it) }
  }
}
