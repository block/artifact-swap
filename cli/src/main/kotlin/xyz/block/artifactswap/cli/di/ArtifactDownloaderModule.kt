package xyz.block.artifactswap.cli.di

import java.nio.file.Path
import java.util.Properties
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import xyz.block.artifactswap.core.config.ArtifactSwapConfig
import xyz.block.artifactswap.core.download.ArtifactDownloader
import xyz.block.artifactswap.core.download.services.ArtifactDownloaderEventStream
import xyz.block.artifactswap.core.download.services.ArtifactRepository
import xyz.block.artifactswap.core.download.services.ArtifactSyncBomLoader
import xyz.block.artifactswap.core.download.services.RealArtifactRepository
import xyz.block.artifactswap.core.download.services.RealArtifactSyncBomLoader
import xyz.block.artifactswap.core.download.services.RealEventStream
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.gradle.GradleProjectsProvider
import xyz.block.artifactswap.core.gradle.GradlePropertiesProvider
import xyz.block.artifactswap.core.gradle.ProjectHashingInfo
import xyz.block.artifactswap.core.gradle.RealGradlePropertiesProvider
import xyz.block.artifactswap.core.gradle.SettingsGradleHashingProjectsProvider
import xyz.block.artifactswap.core.network.ArtifactoryService
import xyz.block.artifactswap.core.shared_services.git.RealSquareGit
import xyz.block.artifactswap.core.shared_services.git.SquareGit

/** Configuration options for the artifact downloader module. */
data class ArtifactDownloaderConfig(val bomVersion: String = "", val settingsGradleFile: Path?)

fun artifactDownloaderModules(
  application: KoinApplication,
  config: ArtifactDownloaderConfig,
): Module {
  return module {
    single<ArtifactDownloaderEventStream> {
      RealEventStream(
        eventstream = get<Eventstream>(),
        ioDispatcher = get<CoroutineDispatcher>(named("IO")),
      )
    }
    single<ArtifactRepository> {
      val artifactSwapConfig = get<ArtifactSwapConfig>()
      val mavenLocalPath =
        Path.of(
          artifactSwapConfig.mavenLocalDirectory.replace(
            "\${user.home}",
            System.getProperty("user.home"),
          )
        )
      RealArtifactRepository(
        localMavenPath = mavenLocalPath,
        artifactoryService = get(),
        ioDispatcher = get<CoroutineDispatcher>(named("IO")),
        objectMapper = get(),
        config = artifactSwapConfig,
      )
    }
    single<SquareGit> {
      RealSquareGit(rootDir = application.directory, context = application.ioDispatcher)
    }
    single<ArtifactSyncBomLoader> {
      RealArtifactSyncBomLoader(
        squareGit = get<SquareGit>(),
        localArtifactRepository = get<ArtifactRepository>(),
        artifactoryService = get<ArtifactoryService>(),
        config = get<ArtifactSwapConfig>(),
      )
    }
    single<GradleProjectsProvider> {
      if (config.settingsGradleFile != null && config.settingsGradleFile.toFile().exists()) {
        SettingsGradleHashingProjectsProvider(
          application.directory,
          config.settingsGradleFile.toRealPath(),
          application.ioDispatcher,
          get<ArtifactSwapConfig>(),
        )
      } else {
        // No-op provider when settings file not available
        object : GradleProjectsProvider {
          override suspend fun getProjectHashingInfos(): Result<List<ProjectHashingInfo>> =
            Result.success(emptyList())

          override suspend fun cleanup() {}
        }
      }
    }
    single<GradlePropertiesProvider> {
      RealGradlePropertiesProvider(get<Properties>(named("gradleProperties")))
    }
    single<ArtifactDownloader> {
      ArtifactDownloader(
        bomLoader = get(),
        artifactEventStream = get(),
        artifactRepository = get(),
        settingsGradleProjectsProvider = get(),
        gradlePropertiesProvider = get(),
        config = get<ArtifactSwapConfig>(),
      )
    }
  }
}

val KoinApplication.artifactDownloader: ArtifactDownloader
  get() = koin.get()
