package xyz.block.artifactswap.idea.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlTag
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import xyz.block.artifactswap.model.ArtifactPathInfo
import xyz.block.artifactswap.model.ArtifactSwapModel
import xyz.block.artifactswap.model.artifactIdToProjectPath
import xyz.block.artifactswap.model.parseArtifactPath
import xyz.block.artifactswap.model.projectPathToDirectory

/** Utility class for finding source files that correspond to classes in swapped artifacts. */
object SourceFileFinder {

  private val logger = Logger.getInstance(SourceFileFinder::class.java)

  /** File extensions to search for code files. */
  private val CODE_EXTENSIONS = listOf(".kt", ".java")

  /** Android resource types that have their own directories (e.g., res/layout/, res/drawable/). */
  private val DIRECTORY_RESOURCES = setOf(
    "layout",
    "drawable",
    "mipmap",
    "anim",
    "animator",
    "menu",
    "raw",
    "xml",
    "font",
    "navigation",
  )

  /** 
   * Android values resource types and their typical file names.
   * These resources are stored in values/ directories (e.g., res/values/strings.xml).
   */
  private val VALUES_RESOURCE_FILE_NAMES = mapOf(
    "string" to listOf("strings.xml"),
    "color" to listOf("colors.xml", "color.xml"),
    "dimen" to listOf("dimens.xml", "dimen.xml"),
    "style" to listOf("styles.xml", "style.xml", "themes.xml"),
    "array" to listOf("arrays.xml", "array.xml"),
    "plurals" to listOf("plurals.xml", "strings.xml"),
    "integer" to listOf("integers.xml", "integer.xml"),
    "bool" to listOf("bools.xml", "bool.xml"),
    "id" to listOf("ids.xml", "id.xml"),
    "attr" to listOf("attrs.xml", "attr.xml"),
    "styleable" to listOf("attrs.xml", "styleable.xml"),
    "fraction" to listOf("fractions.xml", "fraction.xml"),
  )

  /**
   * Finds the source file in the project that corresponds to a class file in a swapped artifact.
   *
   * @param project The IntelliJ project
   * @param artifactFilePath The path to the class file inside the artifact JAR
   * @param sourceElement Optional PSI element to extract more context (e.g., resource type for Android resources)
   * @param model The Artifact Swap model
   * @return The VirtualFile for the source file, or null if not found
   */
  fun findSourceFile(
    project: Project,
    artifactFilePath: String,
    sourceElement: PsiElement? = null,
    model: ArtifactSwapModel,
  ): VirtualFile? {
    val basePath = project.basePath
    if (basePath == null) {
      logger.error("Project base path is null")
      return null
    }

    val pathInfo = model.parseArtifactPath(artifactFilePath)
    if (pathInfo == null) {
      logger.warn("Could not extract artifact ID from path: $artifactFilePath")
      return null
    }

    val (artifactId, _, moduleDir, packagePath, className) = pathInfo

    // Check if this is an Android resource file from transformed directory
    // These paths don't have !/ separator: .../transformed/artifact-name/res/...
    if (artifactFilePath.contains("/res/") && !artifactFilePath.contains("!/")) {
      return findAndroidResourceFromTransformedPath(basePath, artifactFilePath, artifactId, sourceElement, model)
    }

    if (packagePath == null) {
      logger.warn("Could not extract package path from: $artifactFilePath")
      return null
    }

    if (className == null) {
      logger.warn("Could not extract class name from: $artifactFilePath")
      return null
    }

    return findSourceFileInModule(project, basePath, moduleDir, packagePath, className)
  }

  /**
   * Searches for a source file in the given module directory. Searches all source sets in priority
   * order (src/main first, then others). Since Kotlin allows multiple classes per file and files
   * don't need to match class names, we try exact matches first, then fall back to finding any file
   * in the package directory.
   */
  private fun findSourceFileInModule(
    project: Project,
    basePath: String,
    moduleDir: String,
    packagePath: String,
    className: String,
  ): VirtualFile? {
    val localFileSystem = LocalFileSystem.getInstance()

    // Check if this might be an Android resource reference (R.layout.*, R.drawable.*, etc.)
    if (isAndroidResourceReference(packagePath, className)) {
      val resourceFile = findAndroidResourceFile(basePath, moduleDir, className, localFileSystem)
      if (resourceFile != null) {
        return resourceFile
      }
    }

    // Try all source sets in priority order (main first, then others)
    val moduleRoot = localFileSystem.findFileByPath("$basePath/$moduleDir") ?: return null
    val srcDir = moduleRoot.findChild("src") ?: return null

    return SourceSetUtils.getSourceSets(srcDir).firstNotNullOf { sourceSet ->
      tryFindInSourceRoot(
        project,
        basePath,
        moduleDir,
        "src/${sourceSet.name}",
        packagePath,
        className,
        localFileSystem,
      )
    }
  }

  /**
   * Extracts the Android resource type from an XML element (e.g., "string", "color", "dimen").
   * Returns null if the element is not an XmlTag or doesn't represent a resource.
   */
  private fun extractResourceTypeFromElement(element: PsiElement?): String? {
    if (element == null) return null
    
    // Navigate up to find the actual XML tag (user might click on text content)
    var current: PsiElement? = element
    while (current != null && current !is XmlTag) {
      current = current.parent
    }
    
    val tag = current as? XmlTag ?: return null
    
    // The tag name is the resource type (e.g., <string>, <color>, <dimen>)
    return tag.name
  }

  /**
   * Handles Android resource files from Gradle's transformed cache. These are unpacked (not in
   * JARs) with paths like: .../transformed/artifact-name/res/values/values.xml
   *
   * Note: Transformed values.xml files are MERGED files containing all value resources. We need to
   * search for the appropriate source file (strings.xml, colors.xml, etc.). If sourceElement is
   * provided, we can determine the exact resource type (string, color, etc.) to search for the
   * specific file instead of guessing.
   */
  private fun findAndroidResourceFromTransformedPath(
    basePath: String,
    artifactFilePath: String,
    artifactId: String,
    sourceElement: PsiElement?,
    model: ArtifactSwapModel,
  ): VirtualFile? {
    val projectPath = model.artifactIdToProjectPath(artifactId)
    val moduleDir = model.projectPathToDirectory(projectPath)

    // Extract the resource type and filename from the path
    val resIndex = artifactFilePath.indexOf("/res/")
    if (resIndex == -1) {
      logger.warn("No /res/ found in path")
      return null
    }

    val afterRes = artifactFilePath.substring(resIndex + "/res/".length)
    val parts = afterRes.split("/")
    if (parts.size < 2) {
      logger.warn("Invalid resource path structure: $afterRes")
      return null
    }

    val resourceDir = parts.first() // e.g., "values", "layout", "drawable-hdpi"
    val fileName = parts.last() // e.g., "values.xml" (merged) or "strings.xml"

    val localFileSystem = LocalFileSystem.getInstance()
    val moduleRoot = localFileSystem.findFileByPath("$basePath/$moduleDir") ?: return null
    val srcDir = moduleRoot.findChild("src") ?: return null

    // For transformed values.xml (merged file), determine which specific file to search for
    val isMergedValuesFile = resourceDir.startsWith("values") && fileName == "values.xml"
    val fileNamesToTry =
      if (isMergedValuesFile) {
        // If we have the source element, extract the resource type (e.g., <string>, <color>)
        val resourceType = extractResourceTypeFromElement(sourceElement)
        if (resourceType != null) {
          // Look up the specific file names for this resource type
          VALUES_RESOURCE_FILE_NAMES[resourceType] ?: listOf("values.xml")
        } else {
          // Fallback: search common value resource files in priority order
          listOf("strings.xml", "colors.xml", "dimens.xml", "styles.xml", "attrs.xml")
        }
      } else {
        listOf(fileName)
      }

    // Try all source sets (main first)
    for (sourceSet in SourceSetUtils.getSourceSets(srcDir)) {
      val resDir = sourceSet.findChild("res")
      if (resDir != null && resDir.isDirectory) {
        for (fileNameToTry in fileNamesToTry) {
          val result = searchResourceInResDir(resDir, resourceDir, fileNameToTry)
          if (result != null) {
            return result
          }
        }
      }
    }

    logger.warn("No matching resource file found")
    return null
  }

  /**
   * Searches for a resource file in a specific res directory. Handles both values-based resources
   * and directory-based resources with qualifiers.
   */
  private fun searchResourceInResDir(
    resDirFile: VirtualFile,
    resourceDir: String,
    fileName: String,
  ): VirtualFile? {
    // For values directories, check values and values-* variants
    if (resourceDir.startsWith("values")) {
      val valuesDirs =
        resDirFile.children
          .filter { child ->
            child.isDirectory && (child.name == "values" || child.name.startsWith("values-"))
          }
          .sortedBy { child ->
            if (child.name == "values") 0 else 1 // Prioritize plain "values"
          }

      for (valuesDir in valuesDirs) {
        val resourceFile = valuesDir.findChild(fileName)
        if (resourceFile != null && !resourceFile.isDirectory) {
          return resourceFile
        }
      }

      // If not found by filename, return first XML in values directory
      valuesDirs
        .firstOrNull()
        ?.children
        ?.firstOrNull { !it.isDirectory && it.name.endsWith(".xml") }
        ?.let {
          return it
        }
    } else {
      // For other resource types (layout, drawable, etc.), look in matching directories
      val matchingDirs =
        resDirFile.children
          .filter { child ->
            child.isDirectory &&
              (child.name == resourceDir || child.name.startsWith("$resourceDir-"))
          }
          .sortedBy { child ->
            if (child.name == resourceDir) 0 else 1 // Prioritize exact match
          }

      for (dir in matchingDirs) {
        val resourceFile = dir.findChild(fileName)
        if (resourceFile != null && !resourceFile.isDirectory) {
          return resourceFile
        }
      }

      // If not found by filename, return first file in directory
      matchingDirs
        .firstOrNull()
        ?.children
        ?.firstOrNull { !it.isDirectory }
        ?.let {
          return it
        }
    }

    return null
  }

  /**
   * Tries to find a source file in a specific source root (e.g., src/main, src/debug). First tries
   * exact filename match, then falls back to any file in the package.
   */
  private fun tryFindInSourceRoot(
    project: Project,
    basePath: String,
    moduleDir: String,
    sourceRoot: String,
    packagePath: String,
    className: String,
    localFileSystem: LocalFileSystem,
  ): VirtualFile? {
    // Try exact filename match in kotlin/ and java/ subdirectories
    for (lang in listOf("kotlin", "java")) {
      for (extension in CODE_EXTENSIONS) {
        val candidatePath =
          "$basePath/$moduleDir/$sourceRoot/$lang/$packagePath/$className$extension"
        val file = localFileSystem.findFileByPath(candidatePath)
        if (file != null && file.exists()) {
          return file
        }
      }
    }

    // No exact match - search through all files in the package to find the one containing the class
    for (lang in listOf("kotlin", "java")) {
      val packageDirPath = "$basePath/$moduleDir/$sourceRoot/$lang/$packagePath"
      val packageDir = localFileSystem.findFileByPath(packageDirPath)
      if (packageDir != null && packageDir.isDirectory) {
        // Get all Kotlin/Java files in this package
        val candidateFiles =
          packageDir.children.filter { child ->
            !child.isDirectory && CODE_EXTENSIONS.any { child.name.endsWith(it) }
          }

        // Search through each file to find the one containing the class
        val fileWithClass = findFileContainingClass(project, candidateFiles, className)
        if (fileWithClass != null) {
          return fileWithClass
        }

        // If no file contains the class, return the first file as a fallback
        // (useful for top-level functions or other non-class symbols)
        candidateFiles.firstOrNull()?.let {
          return it
        }
      }
    }

    return null
  }

  /**
   * Searches through a list of files to find the one containing the specified class. Returns null
   * if no file contains the class.
   */
  private fun findFileContainingClass(
    project: Project,
    files: List<VirtualFile>,
    className: String,
  ): VirtualFile? {
    val psiManager = PsiManager.getInstance(project)

    for (file in files) {
      val psiFile = psiManager.findFile(file) ?: continue

      // Check Kotlin files
      if (psiFile is KtFile) {
        val classes = psiFile.collectDescendantsOfType<KtClass>()
        if (classes.any { it.name == className }) {
          return file
        }
      }

      // Check Java files
      if (psiFile is PsiJavaFile) {
        if (psiFile.classes.any { it.name == className }) {
          return file
        }
      }
    }

    return null
  }

  /** Checks if the path looks like an Android resource reference (R class). */
  private fun isAndroidResourceReference(packagePath: String, className: String): Boolean {
    // R class or R$ inner classes (R$layout, R$drawable, etc.)
    return className == "R" || className.startsWith("R$") || packagePath.endsWith("/R")
  }

  /**
   * Attempts to find an Android resource file based on the class name. For example, R$layout ->
   * res/layout/file.xml, R$drawable -> res/drawable-hdpi/file.png
   */
  private fun findAndroidResourceFile(
    basePath: String,
    moduleDir: String,
    className: String,
    localFileSystem: LocalFileSystem,
  ): VirtualFile? {
    // Extract resource type from R$type class name
    val resourceType =
      when {
        className.startsWith("R$") -> className.substringAfter("R$")
        else -> return null
      }

    // Directory-based resources (no specific file name needed)
    if (DIRECTORY_RESOURCES.contains(resourceType)) {
      return findResourceInDirectory(
        basePath,
        moduleDir,
        resourceType,
        fileNames = null,
        localFileSystem,
      )
    }

    // Values-based resources (need specific file names like strings.xml, colors.xml)
    val valuesFileNames = VALUES_RESOURCE_FILE_NAMES[resourceType] ?: return null

    return findResourceInDirectory(
      basePath,
      moduleDir,
      "values",
      valuesFileNames,
      localFileSystem,
    )
  }

  /**
   * Gets all res directories in a module by dynamically discovering source sets. Checks
   * src/main/res first, then all other source sets like src/debug/res, src/staging/res, etc.
   */
  private fun getResourceDirectories(
    basePath: String,
    moduleDir: String,
    localFileSystem: LocalFileSystem,
  ): List<VirtualFile> {
    val resDirs = mutableListOf<VirtualFile>()

    val moduleRoot = localFileSystem.findFileByPath("$basePath/$moduleDir") ?: return emptyList()
    val srcDir = moduleRoot.findChild("src") ?: return emptyList()

    for (sourceSet in SourceSetUtils.getSourceSets(srcDir)) {
      val resDir = sourceSet.findChild("res")
      if (resDir != null && resDir.isDirectory) {
        resDirs.add(resDir)
      }
    }

    return resDirs
  }

  /**
   * Finds a resource file in a directory (e.g., res/layout/file.xml, res/drawable-hdpi/file.png, res/values/strings.xml).
   * Searches both exact directory name and qualified variants (e.g., drawable, drawable-hdpi, values-night).
   * All Android resource types can have configuration qualifiers (density, orientation, locale, API level, etc.).
   * 
   * @param resourceType The resource directory name (e.g., "layout", "drawable", "values")
   * @param fileNames Optional list of specific file names to search for (used for values resources like strings.xml)
   */
  private fun findResourceInDirectory(
    basePath: String,
    moduleDir: String,
    resourceType: String,
    fileNames: List<String>?,
    localFileSystem: LocalFileSystem,
  ): VirtualFile? {
    val resDirs = getResourceDirectories(basePath, moduleDir, localFileSystem)

    for (resDirFile in resDirs) {
      // Find all directories matching this resource type (exact match + qualified variants)
      // e.g., "layout", "layout-land", "values", "values-night", "drawable-hdpi"
      val matchingDirs =
        resDirFile.children
          .filter { child ->
            child.isDirectory && (child.name == resourceType || child.name.startsWith("$resourceType-"))
          }
          .sortedBy { child ->
            // Prioritize main variant over qualified variants
            if (child.name == resourceType) 0 else 1
          }

      for (dir in matchingDirs) {
        // If specific file names provided (values resources), search for those
        if (fileNames != null) {
          for (fileName in fileNames) {
            val resourceFile = dir.findChild(fileName)
            if (resourceFile != null && !resourceFile.isDirectory) {
              return resourceFile
            }
          }
          
          // For values directories, fallback to any XML file if no exact match
          if (resourceType == "values") {
            dir.children
              .firstOrNull { !it.isDirectory && it.name.endsWith(".xml") }
              ?.let { return it }
          }
        } else {
          // For directory resources (layout, drawable, etc.), return any file
          dir.children
            .firstOrNull { !it.isDirectory }
            ?.let { return it }
        }
      }
    }
    
    return null
  }

  /** Gets detailed information about where to find the source file for an artifact file. */
  fun getSourceFileInfo(
    project: Project,
    artifactFilePath: String,
    model: ArtifactSwapModel,
  ): Pair<ArtifactPathInfo, VirtualFile?>? {
    val pathInfo = model.parseArtifactPath(artifactFilePath) ?: return null
    val sourceFile = findSourceFile(project, artifactFilePath, sourceElement = null, model)

    return pathInfo to sourceFile
  }
}
