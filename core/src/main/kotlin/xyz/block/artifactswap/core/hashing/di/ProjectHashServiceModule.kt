package xyz.block.artifactswap.core.hashing.di

import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.gradle.GradleProjectsProvider
import xyz.block.artifactswap.core.hashing.ProjectHashService
import xyz.block.artifactswap.core.hashing.services.HashingEventStream
import xyz.block.artifactswap.core.hashing.services.RealHashingEventStream

// Extension properties for accessing common values from KoinApplication
val KoinApplication.ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
    get() = koin.get(named("IO"))

val KoinApplication.defaultDispatcher: CoroutineDispatcher
    get() = koin.get(named("Default"))

internal const val EVENT_STREAM_NAME = "analyticsModuleEventStream"

/**
 * Configuration options for the project hash service module.
 */
data class ProjectHashServiceConfig(
    val placeholder: Boolean = false // Placeholder for future config
)

fun projectHashServiceModules(application: KoinApplication, config: ProjectHashServiceConfig): Module {
    return module {
        single<HashingEventStream> {
            RealHashingEventStream(
                eventstream = get<Eventstream>(named(EVENT_STREAM_NAME)),
                ioDispatcher = get<CoroutineDispatcher>(named("IO"))
            )
        }

        single<ProjectHashService> {
            ProjectHashService(
                gradleProjectsProvider = get<GradleProjectsProvider>(),
                eventStream = get(),
                ioDispatcher = get<CoroutineDispatcher>(named("IO")),
                defaultDispatcher = get<CoroutineDispatcher>(named("Default"))
            )
        }
    }
}

val KoinApplication.projectHashService: ProjectHashService
    get() = koin.get()
