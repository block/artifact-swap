package xyz.block.artifactswap.cli.di

import java.nio.file.Path
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.remover.ArtifactRemover
import xyz.block.artifactswap.core.remover.services.ArtifactRemoverEventStream
import xyz.block.artifactswap.core.remover.services.LocalArtifactRepository
import xyz.block.artifactswap.core.remover.services.RealArtifactRemoverEventStream
import xyz.block.artifactswap.core.remover.services.RealLocalArtifactRepository

/** Configuration options for the artifact remover module. */
data class ArtifactRemoverConfig(val mavenLocalPath: Path)

fun artifactRemoverModules(application: KoinApplication, config: ArtifactRemoverConfig): Module {
  return module {
    single<ArtifactRemoverEventStream> {
      RealArtifactRemoverEventStream(
        eventstream = get<Eventstream>(),
        ioDispatcher = get<CoroutineDispatcher>(named("IO")),
      )
    }

    single<LocalArtifactRepository> {
      RealLocalArtifactRepository(
        localMavenDirectory = config.mavenLocalPath,
        xmlMapper = get(),
        ioContext = get<CoroutineDispatcher>(named("IO")),
      )
    }

    single<ArtifactRemover> {
      ArtifactRemover(artifactEventStream = get(), artifactRepository = get())
    }
  }
}

val KoinApplication.artifactRemover: ArtifactRemover
  get() = koin.get()
