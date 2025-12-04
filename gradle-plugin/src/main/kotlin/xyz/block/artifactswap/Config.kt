package xyz.block.artifactswap

import org.gradle.api.initialization.Settings
import xyz.block.artifactswap.core.config.ArtifactSwapConfig

internal val Settings.artifactSwapConfig: ArtifactSwapConfig
  get() {
    val defaults = ArtifactSwapConfig()
    fun getProperty(key: String, default: String): String =
      providers.gradleProperty(key).getOrElse(default)

    return ArtifactSwapConfig(
      primaryRepositoryName =
        getProperty("artifactswap.primaryRepositoryName", defaults.primaryRepositoryName),
      secondaryRepositoryName =
        getProperty("artifactswap.secondaryRepositoryName", defaults.secondaryRepositoryName),
      primaryArtifactsMavenGroup =
        getProperty("artifactswap.primaryArtifactsMavenGroup", defaults.primaryArtifactsMavenGroup),
      secondaryArtifactsMavenGroup =
        getProperty(
          "artifactswap.secondaryArtifactsMavenGroup",
          defaults.secondaryArtifactsMavenGroup,
        ),
      eventstreamBaseUrl =
        getProperty("artifactswap.eventstreamBaseUrl", defaults.eventstreamBaseUrl),
      artifactoryPublisherTokenFileName =
        getProperty(
          "artifactswap.artifactoryPublisherTokenFileName",
          defaults.artifactoryPublisherTokenFileName,
        ),
      protosGeneratedVersionProperty =
        getProperty(
          "artifactswap.protosGeneratedVersionProperty",
          defaults.protosGeneratedVersionProperty,
        ),
      protosSchemaVersionProperty =
        getProperty(
          "artifactswap.protosSchemaVersionProperty",
          defaults.protosSchemaVersionProperty,
        ),
      artifactoryBaseUrl =
        getProperty("artifactswap.artifactoryBaseUrl", defaults.artifactoryBaseUrl),
    )
  }
