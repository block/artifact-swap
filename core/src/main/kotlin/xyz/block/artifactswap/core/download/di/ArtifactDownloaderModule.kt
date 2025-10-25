package xyz.block.artifactswap.core.download.di

import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.gradle.GradlePropertiesProvider
import xyz.block.artifactswap.core.gradle.RealGradlePropertiesProvider
import kotlinx.coroutines.CoroutineDispatcher
import xyz.block.artifactswap.core.gradle.GradleProjectsProvider
import xyz.block.artifactswap.core.gradle.SettingsGradleHashingProjectsProvider
import xyz.block.artifactswap.core.network.ArtifactoryService
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import xyz.block.artifactswap.core.download.ArtifactDownloader
import xyz.block.artifactswap.core.download.services.ArtifactDownloaderEventStream
import xyz.block.artifactswap.core.download.services.ArtifactRepository
import xyz.block.artifactswap.core.download.services.ArtifactSyncBomLoader
import xyz.block.artifactswap.core.download.services.RealArtifactRepository
import xyz.block.artifactswap.core.download.services.RealArtifactSyncBomLoader
import xyz.block.artifactswap.core.download.services.RealEventStream
import xyz.block.artifactswap.core.download.services.RealSquareGit
import xyz.block.artifactswap.core.download.services.SquareGit
import java.nio.file.Path

// Extension properties for accessing common values from KoinApplication
// These can be provided by the CLI or test modules
val KoinApplication.directory: Path
    get() = koin.get(named("directory"))

val KoinApplication.ioDispatcher: CoroutineDispatcher
    get() = koin.get(named("IO"))

internal const val EVENT_STREAM_NAME = "analyticsModuleEventStream"

/**
 * Configuration options for the artifact downloader module.
 */
data class ArtifactDownloaderConfig(
    val bomVersion: String = "",
    val gradlePropertiesFile: Path,
    val settingsGradleFile: Path,
    val mavenLocalPath: Path,
)

fun artifactDownloaderModules(
    application: KoinApplication,
    config: ArtifactDownloaderConfig
): Module {
    return module {
        single<ArtifactDownloaderEventStream> {
            RealEventStream(
                eventstream = get<Eventstream>(named(EVENT_STREAM_NAME)),
                ioDispatcher = get<CoroutineDispatcher>(named("IO"))
            )
        }
        single<ArtifactRepository> {
            RealArtifactRepository(
                baseArtifactoryUrl = get(named("artifactoryBaseUrl")),
                localMavenPath = config.mavenLocalPath,
                artifactoryService = get(),
                ioDispatcher = get<CoroutineDispatcher>(named("IO")),
                objectMapper = get()
            )
        }
        single<SquareGit> {
            RealSquareGit(
                rootDir = application.directory,
                context = application.ioDispatcher,
            )
        }
        single<ArtifactSyncBomLoader> {
            RealArtifactSyncBomLoader(
                squareGit = get<SquareGit>(),
                localArtifactRepository = get<ArtifactRepository>(),
                artifactoryService = get<ArtifactoryService>(),
            )
        }
        single<GradleProjectsProvider> {
            SettingsGradleHashingProjectsProvider(
                application.directory,
                config.settingsGradleFile.toRealPath(),
                application.ioDispatcher,
            )
        }
        single<GradlePropertiesProvider> {
            RealGradlePropertiesProvider(config.gradlePropertiesFile.toRealPath())
        }
        single<ArtifactDownloader> {
            ArtifactDownloader(
                bomLoader = get(),
                artifactEventStream = get(),
                artifactRepository = get(),
                settingsGradleProjectsProvider = get(),
                gradlePropertiesProvider = get(),
            )
        }
    }
}

val KoinApplication.artifactDownloader: ArtifactDownloader
    get() = koin.get()
