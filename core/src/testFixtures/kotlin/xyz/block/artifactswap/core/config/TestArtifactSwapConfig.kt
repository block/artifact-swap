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
  secondaryRepositoryName: String = ArtifactSwapConfig.SECONDARY_REPOSITORY_NAME,
  primaryArtifactsMavenGroup: String = "com.test.artifacts",
  secondaryArtifactsMavenGroup: String = ArtifactSwapConfig.SECONDARY_ARTIFACTS_MAVEN_GROUP,
  eventstreamBaseUrl: String = ArtifactSwapConfig.EVENTSTREAM_BASE_URL,
  artifactoryPublisherTokenFileName: String = "test-token.txt",
  protosGeneratedVersionProperty: String = ArtifactSwapConfig.PROTOS_GENERATED_VERSION_PROPERTY,
  protosSchemaVersionProperty: String = ArtifactSwapConfig.PROTOS_SCHEMA_VERSION_PROPERTY,
  excludeGradleProjects: List<String> = emptyList(),
  bomSourceBranchName: String = "origin/main",
  artifactoryBaseUrl: String = ArtifactSwapConfig.ARTIFACTORY_BASE_URL,
): ArtifactSwapConfig =
  ArtifactSwapConfig(
    primaryRepositoryName = primaryRepositoryName,
    secondaryRepositoryName = secondaryRepositoryName,
    primaryArtifactsMavenGroup = primaryArtifactsMavenGroup,
    secondaryArtifactsMavenGroup = secondaryArtifactsMavenGroup,
    eventstreamBaseUrl = eventstreamBaseUrl,
    artifactoryPublisherTokenFileName = artifactoryPublisherTokenFileName,
    protosGeneratedVersionProperty = protosGeneratedVersionProperty,
    protosSchemaVersionProperty = protosSchemaVersionProperty,
    excludeGradleProjects = excludeGradleProjects,
    bomSourceBranchName = bomSourceBranchName,
    artifactoryBaseUrl = artifactoryBaseUrl,
  )
