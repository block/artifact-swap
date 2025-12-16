package xyz.block.artifactswap

import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.ProviderFactory
import xyz.block.artifactswap.core.config.ArtifactSwapConfig

internal val Settings.artifactSwapConfig: ArtifactSwapConfig
  get() = readArtifactSwapConfig(providers)

internal val Project.artifactSwapConfig: ArtifactSwapConfig
  get() = readArtifactSwapConfig(providers)

private fun readArtifactSwapConfig(providers: ProviderFactory): ArtifactSwapConfig {
  fun getProperty(key: String, default: String): String =
    providers.gradleProperty(key).getOrElse(default)
  fun getProperty(key: String): String = providers.gradleProperty(key).get()

  return ArtifactSwapConfig(
    primaryRepositoryName = getProperty("artifactswap.primaryRepositoryName"),
    primaryArtifactsMavenGroup = getProperty("artifactswap.primaryArtifactsMavenGroup"),
    secondaryRepositoryName =
      getProperty(
        "artifactswap.secondaryRepositoryName",
        ArtifactSwapConfig.SECONDARY_REPOSITORY_NAME,
      ),
    secondaryArtifactsMavenGroup =
      getProperty(
        "artifactswap.secondaryArtifactsMavenGroup",
        ArtifactSwapConfig.SECONDARY_ARTIFACTS_MAVEN_GROUP,
      ),
    eventstreamBaseUrl =
      getProperty("artifactswap.eventstreamBaseUrl", ArtifactSwapConfig.EVENTSTREAM_BASE_URL),
    artifactoryPublisherTokenFileName =
      getProperty("artifactswap.artifactoryPublisherTokenFileName"),
    protosGeneratedVersionProperty =
      getProperty(
        "artifactswap.protosGeneratedVersionProperty",
        ArtifactSwapConfig.PROTOS_GENERATED_VERSION_PROPERTY,
      ),
    protosSchemaVersionProperty =
      getProperty(
        "artifactswap.protosSchemaVersionProperty",
        ArtifactSwapConfig.PROTOS_SCHEMA_VERSION_PROPERTY,
      ),
    excludeGradleProjects = emptyList(),
    bomSourceBranchName = getProperty("artifactswap.bomSourceBranchName"),
    artifactoryBaseUrl = getProperty("artifactswap.artifactoryBaseUrl"),
  )
}
