package xyz.block.artifactswap.cli.di

import java.nio.file.Path
import java.util.Properties
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import org.koin.dsl.module
import xyz.block.artifactswap.cli.options.CommonOptions
import xyz.block.artifactswap.core.config.ArtifactSwapConfig

internal fun commonModule(commonOptions: CommonOptions) = module {
  single(named("directory")) { commonOptions.directory }

  single(named("dryRun")) { commonOptions.dryRun }

  // Create ArtifactSwapConfig from gradle properties loaded in GradleModule
  single<ArtifactSwapConfig> {
    createArtifactSwapConfig(get<Properties>(named("gradleProperties")))
  }
}

// Creates ArtifactSwapConfig from gradle properties
private fun createArtifactSwapConfig(properties: Properties): ArtifactSwapConfig {
  fun getProperty(key: String, default: String): String = properties.getProperty(key) ?: default
  fun getProperty(key: String): String =
    properties.getProperty(key)
      ?: throw IllegalStateException(
        "Required gradle property '$key' is not set. Please define it in gradle.properties."
      )

  return ArtifactSwapConfig(
    primaryRepositoryName = getProperty("artifactswap.primaryRepositoryName"),
    primaryArtifactsMavenGroup = getProperty("artifactswap.primaryArtifactsMavenGroup"),
    eventstreamBaseUrl =
      getProperty("artifactswap.eventstreamBaseUrl", ArtifactSwapConfig.EVENTSTREAM_BASE_URL),
    artifactoryPublisherTokenFileName =
      getProperty("artifactswap.artifactoryPublisherTokenFileName"),
    excludeGradleProjects = emptyList(),
    artifactoryBaseUrl =
      getProperty("artifactswap.artifactoryBaseUrl", ArtifactSwapConfig.ARTIFACTORY_BASE_URL),
    bomSourceBranchName = getProperty("artifactswap.bomSourceBranchName"),
    mavenLocalDirectory =
      getProperty(
          "artifactswap.mavenLocalDirectory",
          ArtifactSwapConfig.DEFAULT_MAVEN_LOCAL_DIRECTORY,
        )
        .replace("\${user.home}", System.getProperty("user.home")),
  )
}

val KoinApplication.directory: Path
  get() = koin.get(named("directory"))

val KoinApplication.dryRun: Boolean
  get() = koin.get(named("dryRun"))

val KoinApplication.artifactSwapConfig: ArtifactSwapConfig
  get() = koin.get()
