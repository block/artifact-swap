package xyz.block.artifactswap.core.config

/**
 * Creates an [ArtifactSwapConfig] with test-friendly default values.
 *
 * This function provides sensible defaults for all required configuration parameters, making it
 * easy to create config instances in tests without having to specify every parameter.
 *
 * Individual parameters can be overridden by providing them as arguments.
 *
 * @return A fully configured [ArtifactSwapConfig] suitable for testing
 */
fun testArtifactSwapConfig(
  primaryRepositoryName: String = "test-primary-repo",
  primaryArtifactsMavenGroup: String = "com.test.artifacts",
  eventstreamBaseUrl: String = ArtifactSwapConfig.EVENTSTREAM_BASE_URL,
  artifactoryPublisherTokenFileName: String = "test-token.txt",
  excludeGradleProjects: List<String> = emptyList(),
  bomSourceBranchName: String = "origin/main",
  artifactoryBaseUrl: String = ArtifactSwapConfig.ARTIFACTORY_BASE_URL,
  mavenLocalDirectory: String = "${System.getProperty("user.home")}/.m2/repository",
): ArtifactSwapConfig =
  ArtifactSwapConfig(
    primaryRepositoryName = primaryRepositoryName,
    primaryArtifactsMavenGroup = primaryArtifactsMavenGroup,
    eventstreamBaseUrl = eventstreamBaseUrl,
    artifactoryPublisherTokenFileName = artifactoryPublisherTokenFileName,
    excludeGradleProjects = excludeGradleProjects,
    bomSourceBranchName = bomSourceBranchName,
    artifactoryBaseUrl = artifactoryBaseUrl,
    mavenLocalDirectory = mavenLocalDirectory,
  )
