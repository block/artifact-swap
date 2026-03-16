package xyz.block.artifactswap.gradle.internal

import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.ProviderFactory
import xyz.block.artifactswap.core.config.ArtifactSwapConfig

public val Settings.artifactSwapConfig: ArtifactSwapConfig
  get() = readArtifactSwapConfig(providers)

public val Project.artifactSwapConfig: ArtifactSwapConfig
  get() = readArtifactSwapConfig(providers)

private fun readArtifactSwapConfig(providers: ProviderFactory): ArtifactSwapConfig {
  fun getProperty(key: String, default: String): String =
    providers.gradleProperty(key).getOrElse(default)
  fun getProperty(key: String): String = providers.gradleProperty(key).get()

  return ArtifactSwapConfig(
    primaryRepositoryName = getProperty("artifactswap.primaryRepositoryName"),
    primaryArtifactsMavenGroup = getProperty("artifactswap.primaryArtifactsMavenGroup"),
    eventstreamBaseUrl =
      getProperty("artifactswap.eventstreamBaseUrl", ArtifactSwapConfig.EVENTSTREAM_BASE_URL),
    artifactoryPublisherTokenFileName =
      getProperty("artifactswap.artifactoryPublisherTokenFileName"),
    excludeGradleProjects = emptyList(),
    bomSourceBranchName = getProperty("artifactswap.bomSourceBranchName"),
    artifactoryBaseUrl = getProperty("artifactswap.artifactoryBaseUrl"),
    mavenLocalDirectory =
      getProperty(
          "artifactswap.mavenLocalDirectory",
          ArtifactSwapConfig.DEFAULT_MAVEN_LOCAL_DIRECTORY,
        )
        .replace("\${user.home}", System.getProperty("user.home")),
  )
}
