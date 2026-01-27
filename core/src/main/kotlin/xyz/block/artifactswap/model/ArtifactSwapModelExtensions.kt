package xyz.block.artifactswap.model

/** Checks if a file path is inside a swapped artifact JAR. */
fun ArtifactSwapModel.isSwappedArtifactPath(filePath: String): Boolean =
  ArtifactPathParser.isSwappedArtifactPath(filePath, mavenGroup)

/** Extracts the artifact ID from either Maven Local or Gradle transform cache path. */
fun ArtifactSwapModel.extractArtifactId(filePath: String): String? =
  ArtifactPathParser.extractArtifactId(filePath, mavenGroup)

/** Parses an artifact file path and extracts all relevant information in a single pass. */
fun ArtifactSwapModel.parseArtifactPath(artifactFilePath: String): ArtifactPathInfo? =
  ArtifactPathParser.parseArtifactPath(artifactFilePath, mavenGroup)

/** Converts an artifact ID to a Gradle project path. */
fun ArtifactSwapModel.artifactIdToProjectPath(artifactId: String): String =
  ArtifactPathParser.artifactIdToProjectPath(artifactId)

/** Converts a Gradle project path to a file system directory path. */
fun ArtifactSwapModel.projectPathToDirectory(projectPath: String): String =
  ArtifactPathParser.projectPathToDirectory(projectPath)
