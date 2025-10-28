package xyz.block.artifactswap.core.task_finder.di

import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.task_finder.TaskFinderService
import xyz.block.artifactswap.core.task_finder.services.RealTaskFinderEventStream
import xyz.block.artifactswap.core.task_finder.services.TaskFinderEventStream

/**
 * Configuration for TaskFinderService module.
 */
data class TaskFinderServiceConfig(
    val placeholder: Boolean = false
)

/**
 * Creates Koin modules for TaskFinderService.
 */
fun taskFinderServiceModules(
    application: KoinApplication,
    config: TaskFinderServiceConfig
): Module {
    return module {
        single<TaskFinderEventStream> {
            RealTaskFinderEventStream(
                eventstream = get<Eventstream>(named("analyticsModuleEventStream")),
                ioDispatcher = get<kotlinx.coroutines.CoroutineDispatcher>(named("IO"))
            )
        }

        single {
            TaskFinderService(
                eventStream = get(),
                ioDispatcher = get<kotlinx.coroutines.CoroutineDispatcher>(named("IO"))
            )
        }
    }
}

/**
 * Extension property to get TaskFinderService from KoinApplication.
 */
val KoinApplication.taskFinderService: TaskFinderService
    get() = koin.get()
