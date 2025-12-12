package xyz.block.artifactswap.cli.di

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import xyz.block.artifactswap.core.config.ArtifactSwapConfig
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.network.ArtifactoryEndpoints
import xyz.block.artifactswap.core.publisher.ArtifactoryBomRepository
import xyz.block.artifactswap.core.publisher.BomPublisher
import xyz.block.artifactswap.core.publisher.BomRepository
import xyz.block.artifactswap.core.publisher.LocalBomRepository
import xyz.block.artifactswap.core.publisher.services.BomPublisherEventStream
import xyz.block.artifactswap.core.publisher.services.ProjectHashReader
import xyz.block.artifactswap.core.publisher.services.RealBomPublisherEventStream
import xyz.block.artifactswap.core.publisher.services.RealProjectHashReader
import xyz.block.artifactswap.core.repository.RealLocalArtifactRepository

/** Configuration options for the BOM publisher module. */
data class BomPublisherConfig(val dryRun: Boolean = false, val local: Boolean = false)

fun bomPublisherModules(application: KoinApplication, config: BomPublisherConfig): Module {
  return module {
    single<ProjectHashReader> { RealProjectHashReader() }

    single<BomPublisherEventStream> {
      RealBomPublisherEventStream(
        eventstream = get<Eventstream>(),
        ioDispatcher = get<CoroutineDispatcher>(named("IO")),
      )
    }

    // Provide the appropriate BomRepository implementation based on local flag
    single<BomRepository> {
      if (config.local) {
        // Provide LocalArtifactRepository for local mode
        // mavenDirectory is derived from config.mavenLocalDirectory inside
        // RealLocalArtifactRepository
        val localArtifactRepository =
          RealLocalArtifactRepository(
            xmlMapper = get<ObjectMapper>(),
            ioContext = get<CoroutineDispatcher>(named("IO")),
            config = get<ArtifactSwapConfig>(),
          )

        LocalBomRepository(
          localArtifactRepository = localArtifactRepository,
          xmlMapper = get<ObjectMapper>(),
          config = get<ArtifactSwapConfig>(),
        )
      } else {
        ArtifactoryBomRepository(
          artifactoryEndpoints = get<ArtifactoryEndpoints>(),
          config = get<ArtifactSwapConfig>(),
        )
      }
    }

    single<BomPublisher> {
      BomPublisher(
        projectHashReader = get(),
        bomRepository = get<BomRepository>(),
        eventStream = get(),
        config = get<ArtifactSwapConfig>(),
        dryRun = config.dryRun,
      )
    }
  }
}

val KoinApplication.bomPublisher: BomPublisher
  get() = koin.get()
