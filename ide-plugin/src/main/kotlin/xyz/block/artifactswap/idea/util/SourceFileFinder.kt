package xyz.block.artifactswap.idea.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
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

  /**
   * Finds the source file in the project that corresponds to a class file in a swapped artifact.
   *
   * @param project The IntelliJ project
   * @param artifactFilePath The path to the class file inside the artifact JAR
   * @param model The Artifact Swap model
   * @return The VirtualFile for the source file, or null if not found
   */
  fun findSourceFile(
    project: Project,
    artifactFilePath: String,
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
      return findAndroidResourceFromTransformedPath(basePath, artifactFilePath, artifactId, model)
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
   * Searches for a source file in the given module directory. Tries src/main first, then falls back
   * to searching any other src subdirectories. Since Kotlin allows multiple classes per file and
   * files don't need to match class names, we try exact matches first, then fall back to finding
   * any file in the package directory.
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

    // Step 1: Try src/main/* first (most common location)
    val mainSourceResult =
      tryFindInSourceRoot(
        project,
        basePath,
        moduleDir,
        "src/main",
        packagePath,
        className,
        localFileSystem,
      )
    if (mainSourceResult != null) {
      return mainSourceResult
    }

    // Step 2: Try any other src/* directories dynamically
    val moduleRoot = localFileSystem.findFileByPath("$basePath/$moduleDir") ?: return null
    val srcDir = moduleRoot.findChild("src") ?: return null

    // Get all subdirectories of src/ except "main" (already tried)
    val otherSourceSets = srcDir.children.filter { it.isDirectory && it.name != "main" }

    for (sourceSet in otherSourceSets) {
      val result =
        tryFindInSourceRoot(
          project,
          basePath,
          moduleDir,
          "src/${sourceSet.name}",
          packagePath,
          className,
          localFileSystem,
        )
      if (result != null) {
        return result
      }
    }

    return null
  }

  /**
   * Handles Android resource files from Gradle's transformed cache. These are unpacked (not in
   * JARs) with paths like: .../transformed/artifact-name/res/values/values.xml
   *
   * Note: Transformed values.xml files are MERGED files containing all value resources. We need to
   * search for the appropriate source file (strings.xml, colors.xml, etc.)
   */
  private fun findAndroidResourceFromTransformedPath(
    basePath: String,
    artifactFilePath: String,
    artifactId: String,
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

    // For transformed values.xml (merged file), try common value resource files
    val isMergedValuesFile = resourceDir.startsWith("values") && fileName == "values.xml"
    val fileNamesToTry =
      if (isMergedValuesFile) {
        // Search for common value resource files in priority order
        listOf("strings.xml", "colors.xml", "dimens.xml", "styles.xml", "attrs.xml")
      } else {
        listOf(fileName)
      }

    // Step 1: Try src/main/res first (most common)
    val mainRes = localFileSystem.findFileByPath("$basePath/$moduleDir/src/main/res")
    if (mainRes != null && mainRes.isDirectory) {
      for (fileNameToTry in fileNamesToTry) {
        val result = searchResourceInResDir(mainRes, resourceDir, fileNameToTry)
        if (result != null) {
          return result
        }
      }
    }

    // Step 2: Try all other source sets
    val otherSourceSets = srcDir.children.filter { it.isDirectory && it.name != "main" }
    for (sourceSet in otherSourceSets) {
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

    // Handle directory-based resources
    val directoryResources =
      mapOf(
        "layout" to "layout",
        "drawable" to "drawable*",
        "mipmap" to "mipmap*",
        "anim" to "anim",
        "animator" to "animator",
        "menu" to "menu",
        "raw" to "raw",
        "xml" to "xml",
        "font" to "font",
        "navigation" to "navigation",
      )

    if (directoryResources.containsKey(resourceType)) {
      return findDirectoryResource(
        basePath,
        moduleDir,
        directoryResources[resourceType]!!,
        localFileSystem,
      )
    }

    // Handle values-based resources
    val valuesResources =
      mapOf(
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

    if (valuesResources.containsKey(resourceType)) {
      return findValuesResource(
        basePath,
        moduleDir,
        valuesResources[resourceType]!!,
        localFileSystem,
      )
    }

    return null
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

    // Try src/main/res first
    val mainRes = localFileSystem.findFileByPath("$basePath/$moduleDir/src/main/res")
    if (mainRes != null && mainRes.isDirectory) {
      resDirs.add(mainRes)
    }

    // Try all other src/*/res directories
    val otherSourceSets = srcDir.children.filter { it.isDirectory && it.name != "main" }
    for (sourceSet in otherSourceSets) {
      val resDir = sourceSet.findChild("res")
      if (resDir != null && resDir.isDirectory) {
        resDirs.add(resDir)
      }
    }

    return resDirs
  }

  /**
   * Finds a resource file in a directory (e.g., res/layout/file.xml, res/drawable-hdpi/file.png).
   * Supports wildcards for variant directories like drawable-hdpi, drawable-xxhdpi, etc.
   */
  private fun findDirectoryResource(
    basePath: String,
    moduleDir: String,
    resourceSubDir: String,
    localFileSystem: LocalFileSystem,
  ): VirtualFile? {
    val resDirs = getResourceDirectories(basePath, moduleDir, localFileSystem)

    for (resDirFile in resDirs) {
      // Check if this is a wildcard search (e.g., "drawable*", "mipmap*")
      if (resourceSubDir.endsWith("*")) {
        val prefix = resourceSubDir.dropLast(1)
        // Find all directories matching the prefix (e.g., drawable, drawable-hdpi, drawable-xxhdpi)
        val matchingDirs =
          resDirFile.children
            .filter { child ->
              child.isDirectory && (child.name == prefix || child.name.startsWith("$prefix-"))
            }
            .sortedBy { child ->
              // Prioritize exact match (e.g., "drawable" before "drawable-hdpi")
              if (child.name == prefix) 0 else 1
            }

        // Return first resource file found in any matching directory
        for (dir in matchingDirs) {
          dir.children
            .firstOrNull { !it.isDirectory }
            ?.let {
              return it
            }
        }
      } else {
        // Direct subdirectory (e.g., "layout")
        val resourceDir = resDirFile.findChild(resourceSubDir)
        if (resourceDir != null && resourceDir.isDirectory) {
          // Return first resource file found
          resourceDir.children
            .firstOrNull { !it.isDirectory }
            ?.let {
              return it
            }
        }
      }
    }
    return null
  }

  /**
   * Finds a values resource file (e.g., res/values/strings.xml). Searches all values and values-*
   * directories for the resource files.
   */
  private fun findValuesResource(
    basePath: String,
    moduleDir: String,
    fileNames: List<String>,
    localFileSystem: LocalFileSystem,
  ): VirtualFile? {
    val resDirs = getResourceDirectories(basePath, moduleDir, localFileSystem)

    for (resDirFile in resDirs) {
      // Find all values and values-* directories
      val valuesDirs =
        resDirFile.children
          .filter { child ->
            child.isDirectory && (child.name == "values" || child.name.startsWith("values-"))
          }
          .sortedBy { child ->
            // Prioritize plain "values" over variants
            if (child.name == "values") 0 else 1
          }

      for (valuesDir in valuesDirs) {
        // Try each possible filename
        for (fileName in fileNames) {
          val resourceFile = valuesDir.findChild(fileName)
          if (resourceFile != null && !resourceFile.isDirectory) {
            return resourceFile
          }
        }

        // If no exact match, return first XML file in the values directory
        valuesDir.children
          .firstOrNull { !it.isDirectory && it.name.endsWith(".xml") }
          ?.let {
            return it
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
    val sourceFile = findSourceFile(project, artifactFilePath, model)

    return pathInfo to sourceFile
  }
}
