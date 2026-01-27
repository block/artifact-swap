package xyz.block.artifactswap.model

/**
 * Parsed information from an artifact file path.
 *
 * @property artifactId The artifact ID (e.g., "my-module")
 * @property projectPath The Gradle project path (e.g., ":my:module")
 * @property moduleDir The module directory path (e.g., "my/module")
 * @property packagePath The package path inside the JAR (e.g., "com/example"), null if not
 *   applicable
 * @property className The class name (e.g., "MyClass"), null if not applicable
 */
data class ArtifactPathInfo(
  val artifactId: String,
  val projectPath: String,
  val moduleDir: String,
  val packagePath: String?,
  val className: String?,
)

/**
 * Parses artifact file paths from Maven Local and Gradle transform cache into structured
 * information.
 *
 * Supports two artifact storage locations:
 * 1. Maven Local: `~/.m2/repository/{group}/{artifactId}/{version}/{artifactId}-{version}.jar`
 * 2. Gradle transform cache:
 *    `~/.gradle/caches/.../transformed/{artifactId}-VERSION/jars/classes.jar!/...`
 */
object ArtifactPathParser {
  private const val JAR_MARKER = "/jars/classes.jar!/"

  /**
   * Checks if a file path is inside a swapped artifact JAR.
   *
   * Swapped artifacts can be in two locations:
   * 1. Maven Local: `~/.m2/repository/{group}/{artifact}/{version}/...`
   * 2. Gradle transform cache:
   *    `~/.gradle/caches/.../transformed/{artifact}-VERSION/jars/classes.jar!/...`
   */
  fun isSwappedArtifactPath(filePath: String, mavenGroup: String): Boolean {
    // Check Maven Local with our artifact group
    val mavenGroupPath = mavenGroup.replace('.', '/')
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
  fun extractArtifactId(filePath: String, mavenGroup: String): String? {
    // Try Maven Local first
    val mavenGroupPath = mavenGroup.replace('.', '/')
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
   * @param mavenGroup The Maven group ID for swapped artifacts
   * @return Parsed artifact path information, or null if the artifact ID cannot be extracted
   */
  fun parseArtifactPath(artifactFilePath: String, mavenGroup: String): ArtifactPathInfo? {
    val artifactId = extractArtifactId(artifactFilePath, mavenGroup) ?: return null
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
