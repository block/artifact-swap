package xyz.block.artifactswap.core.publisher.di

import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.network.ArtifactoryEndpoints
import xyz.block.artifactswap.core.publisher.BomPublisher
import xyz.block.artifactswap.core.publisher.services.BomPublisherEventStream
import xyz.block.artifactswap.core.publisher.services.ProjectHashReader
import xyz.block.artifactswap.core.publisher.services.RealBomPublisherEventStream
import xyz.block.artifactswap.core.publisher.services.RealProjectHashReader

// Extension properties for accessing common values from KoinApplication
val KoinApplication.ioDispatcher: CoroutineDispatcher
    get() = koin.get(named("IO"))

internal const val EVENT_STREAM_NAME = "analyticsModuleEventStream"

/**
 * Configuration options for the BOM publisher module.
 */
data class BomPublisherConfig(
    val dryRun: Boolean = false
)

fun bomPublisherModules(application: KoinApplication, config: BomPublisherConfig): Module {
    return module {
        single<ProjectHashReader> {
            RealProjectHashReader()
        }

        single<BomPublisherEventStream> {
            RealBomPublisherEventStream(
                eventstream = get<Eventstream>(named(EVENT_STREAM_NAME)),
                ioDispatcher = get<CoroutineDispatcher>(named("IO"))
            )
        }

        single<BomPublisher> {
            BomPublisher(
                projectHashReader = get(),
                artifactoryEndpoints = get<ArtifactoryEndpoints>(),
                eventStream = get(),
                dryRun = config.dryRun
            )
        }
    }
}

val KoinApplication.bomPublisher: BomPublisher
    get() = koin.get()
