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

// Creates ArtifactSwapConfig from gradle properties with appropriate defaults
private fun createArtifactSwapConfig(properties: Properties): ArtifactSwapConfig {
  val defaults = ArtifactSwapConfig()

  fun getProperty(key: String, default: String): String = properties.getProperty(key) ?: default

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
      getProperty("artifactswap.protosSchemaVersionProperty", defaults.protosSchemaVersionProperty),
    artifactoryBaseUrl =
      getProperty("artifactswap.artifactoryBaseUrl", defaults.artifactoryBaseUrl),
    bomSourceBranchName =
      getProperty("artifactswap.bomSourceBranchName", defaults.bomSourceBranchName),
  )
}

val KoinApplication.directory: Path
  get() = koin.get(named("directory"))

val KoinApplication.dryRun: Boolean
  get() = koin.get(named("dryRun"))

val KoinApplication.artifactSwapConfig: ArtifactSwapConfig
  get() = koin.get()
