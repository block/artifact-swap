package xyz.block.artifactswap.idea.config

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import xyz.block.artifactswap.idea.gradle.ArtifactSwapService
import xyz.block.artifactswap.model.ArtifactSwapModel

/**
 * Parsed information from an artifact file path.
 *
 * @property artifactId The artifact ID (e.g., "my-module")
 * @property projectPath The Gradle project path (e.g., ":my:module")
 * @property moduleDir The module directory path (e.g., "my/module")
 * @property packagePath The package path inside the JAR (e.g., "com/example"), null if not
 *   applicable
 * @property className The class name (e.g., "MyClass"), null if not applicable
 * @property sourceFile The resolved source file in the project, null if not resolved or not
 *   applicable
 */
data class ArtifactPathInfo(
  val artifactId: String,
  val projectPath: String,
  val moduleDir: String,
  val packagePath: String?,
  val className: String?,
  val sourceFile: VirtualFile? = null,
)

/**
 * Configuration for the Artifact Swap IDE plugin.
 *
 * Retrieves configuration from the Gradle build via [ArtifactSwapModel], which provides:
 * - Maven group ID for swapped artifacts
 * - BOM version for swapped artifacts
 */
data class ArtifactSwapConfig(
  /** Maven group ID for the main artifacts */
  val primaryArtifactsMavenGroup: String,

  /** BOM version for swapped artifacts */
  val bomVersion: String,
) {
  companion object {
    /** Retrieves the Artifact Swap configuration from Gradle sync data. */
    fun fromProject(project: Project): ArtifactSwapConfig? {
      val service = ArtifactSwapService.getInstance(project)
      val model = service.model ?: return null

      return ArtifactSwapConfig(
        primaryArtifactsMavenGroup = model.mavenGroup,
        bomVersion = model.bomVersion,
      )
    }

    private const val JAR_MARKER = "/jars/classes.jar!/"
  }

  /**
   * Checks if a file path is inside a swapped artifact JAR.
   *
   * Swapped artifacts can be in two locations:
   * 1. Maven Local:
   *    ~/.m2/repository/com/squareup/cash/artifacts/artifact_name/VERSION/artifact_name-VERSION-sources.jar
   * 2. Gradle transform cache:
   *    ~/.gradle/caches/.../transformed/artifact_name-VERSION/jars/classes.jar!/...
   */
  fun isSwappedArtifactPath(filePath: String): Boolean {
    // Check Maven Local with our artifact group
    val mavenGroupPath = primaryArtifactsMavenGroup.replace('.', '/')
    if (filePath.contains("/.m2/repository/$mavenGroupPath/")) {
      return true
    }

    // Check Gradle transform cache by looking for the specific JAR structure
    if (filePath.contains("/.gradle/caches/") && filePath.contains("/jars/classes.jar!/")) {
      return true
    }

    return false
  }

  /**
   * Extracts the artifact ID from either Maven Local or Gradle transform cache path.
   *
   * Maven Local format:
   * `~/.m2/repository/{group}/{artifactId}/{version}/{artifactId}-{version}-sources.jar`
   *
   * Transform cache format:
   * `~/.gradle/caches/.../transformed/artifact-name-VERSION/jars/classes.jar!/...`
   */
  fun extractArtifactId(filePath: String): String? {
    // Try Maven Local first
    val mavenGroupPath = primaryArtifactsMavenGroup.replace('.', '/')
    val m2Pattern = "/.m2/repository/$mavenGroupPath/"
    val m2Index = filePath.indexOf(m2Pattern)
    if (m2Index != -1) {
      // Extract: .../group/artifactId/version/...
      val afterGroup = filePath.substring(m2Index + m2Pattern.length)
      val artifactId = afterGroup.substringBefore('/')
      return artifactId
    }

    // Try Gradle transform cache
    val transformedDir = extractTransformedDirectory(filePath) ?: return null

    // Split on the last dash: everything before is the artifact name
    val lastDashIndex = transformedDir.lastIndexOf('-')
    return if (lastDashIndex != -1) {
      transformedDir.take(lastDashIndex)
    } else {
      transformedDir
    }
  }

  /**
   * Extracts the artifact directory name from a Gradle transform cache path.
   *
   * The Gradle transform cache stores artifacts in a structure like:
   * `~/.gradle/caches/.../transformed/artifact-VERSION/jars/classes.jar!/...`
   *
   * This function splits on "jars/classes.jar!/" and extracts the last directory segment before it,
   * which contains the artifact-VERSION.
   *
   * Example: `~/.gradle/caches/.../transformed/artifact-VERSION/jars/classes.jar!/...` Returns:
   * `artifact-VERSION`
   */
  private fun extractTransformedDirectory(filePath: String): String? {
    // Split on the JAR path marker and get the last directory segment before it
    if (!filePath.contains(JAR_MARKER)) return null

    val pathBeforeJar = filePath.substringBefore(JAR_MARKER)
    return pathBeforeJar.substringAfterLast('/')
  }

  /**
   * Extracts the package path from a file inside a JAR.
   *
   * Example path: .../classes.jar!/com/example/MyClass.class Returns: com/example
   */
  fun extractPackagePath(filePath: String): String? {
    if (!filePath.contains("!/")) return null

    val pathInJar = filePath.substringAfter("!/")
    return pathInJar.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }
  }

  /** Extracts the class name (without extension) from a file path. */
  fun extractClassName(filePath: String): String {
    val fileName = filePath.substringAfterLast('/')
    return fileName.substringBeforeLast('.')
  }

  /**
   * Converts an artifact ID to a Gradle project path. Example: module_submodule ->
   * :module:submodule
   */
  fun artifactIdToProjectPath(artifactId: String): String {
    return ":${artifactId.replace('_', ':')}"
  }

  /**
   * Converts a Gradle project path to a file system directory path. Example: :module:submodule ->
   * module/submodule
   */
  fun projectPathToDirectory(projectPath: String): String {
    return projectPath.removePrefix(":").replace(':', '/')
  }

  /**
   * Parses an artifact file path and extracts all relevant information in a single pass.
   *
   * This method is more efficient than calling individual extract* methods separately, as it parses
   * the path string only once.
   *
   * @param artifactFilePath The path to a file in an artifact JAR
   * @return Parsed artifact path information, or null if the artifact ID cannot be extracted
   */
  fun parseArtifactPath(artifactFilePath: String): ArtifactPathInfo? {
    val artifactId = extractArtifactId(artifactFilePath) ?: return null
    val projectPath = artifactIdToProjectPath(artifactId)
    val moduleDir = projectPathToDirectory(projectPath)
    val packagePath = extractPackagePath(artifactFilePath)
    val className = extractClassName(artifactFilePath)

    return ArtifactPathInfo(
      artifactId = artifactId,
      projectPath = projectPath,
      moduleDir = moduleDir,
      packagePath = packagePath,
      className = className,
    )
  }
}
