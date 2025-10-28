package xyz.block.artifactswap.core.task_runner.di

import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.task_runner.TaskRunnerService
import xyz.block.artifactswap.core.task_runner.services.RealTaskRunnerEventStream
import xyz.block.artifactswap.core.task_runner.services.TaskRunnerEventStream

/**
 * Configuration for TaskRunnerService module.
 */
data class TaskRunnerServiceConfig(
    val placeholder: Boolean = false
)

/**
 * Creates Koin modules for TaskRunnerService.
 */
fun taskRunnerServiceModules(
    application: KoinApplication,
    config: TaskRunnerServiceConfig
): Module {
    return module {
        single<TaskRunnerEventStream> {
            RealTaskRunnerEventStream(
                eventstream = get<Eventstream>(named("analyticsModuleEventStream")),
                ioDispatcher = get<kotlinx.coroutines.CoroutineDispatcher>(named("IO"))
            )
        }

        single {
            TaskRunnerService(
                eventStream = get(),
                ioDispatcher = get<kotlinx.coroutines.CoroutineDispatcher>(named("IO"))
            )
        }
    }
}

/**
 * Extension property to get TaskRunnerService from KoinApplication.
 */
val KoinApplication.taskRunnerService: TaskRunnerService
    get() = koin.get()
