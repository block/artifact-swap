package xyz.block.artifactswap.cli.di

import java.nio.file.Path
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
import xyz.block.artifactswap.core.network.ArtifactoryService
import xyz.block.artifactswap.core.shared_services.git.RealSquareGit
import xyz.block.artifactswap.core.shared_services.git.SquareGit

/** Configuration options for the artifact downloader module. */
data class ArtifactDownloaderConfig(val bomVersion: String = "")

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
    single<ArtifactDownloader> {
      ArtifactDownloader(
        bomLoader = get(),
        artifactEventStream = get(),
        artifactRepository = get(),
        config = get<ArtifactSwapConfig>(),
      )
    }
  }
}

val KoinApplication.artifactDownloader: ArtifactDownloader
  get() = koin.get()
