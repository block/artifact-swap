package xyz.block.artifactswap.cli.di

import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import xyz.block.artifactswap.core.artifact_checker.ArtifactCheckerService
import xyz.block.artifactswap.core.artifact_checker.services.ArtifactCheckerEventStream
import xyz.block.artifactswap.core.artifact_checker.services.RealArtifactCheckerEventStream
import xyz.block.artifactswap.core.eventstream.Eventstream

/** Configuration for ArtifactCheckerService module. */
data class ArtifactCheckerServiceConfig(val placeholder: Boolean = false)

/** Creates Koin modules for ArtifactCheckerService. */
fun artifactCheckerServiceModules(
  application: KoinApplication,
  config: ArtifactCheckerServiceConfig,
): Module {
  return module {
    single<ArtifactCheckerEventStream> {
      RealArtifactCheckerEventStream(
        eventstream = get<Eventstream>(named("analyticsModuleEventStream")),
        ioDispatcher = get<CoroutineDispatcher>(named("IO")),
      )
    }

    single {
      ArtifactCheckerService(
        artifactoryService = get(),
        eventStream = get(),
        ioDispatcher = get<CoroutineDispatcher>(named("IO")),
      )
    }
  }
}

/** Extension property to get ArtifactCheckerService from KoinApplication. */
val KoinApplication.artifactCheckerService: ArtifactCheckerService
  get() = koin.get()
