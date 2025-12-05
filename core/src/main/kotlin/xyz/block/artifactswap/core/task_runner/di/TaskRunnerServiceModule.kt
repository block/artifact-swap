package xyz.block.artifactswap.core.task_runner.di

import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import xyz.block.artifactswap.core.eventstream.EventStreamAdapter
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.task_runner.TaskRunnerService

/** Configuration for TaskRunnerService module. */
data class TaskRunnerServiceConfig(val placeholder: Boolean = false)

/** Creates Koin modules for TaskRunnerService. */
fun taskRunnerServiceModules(
  application: KoinApplication,
  config: TaskRunnerServiceConfig,
): Module {
  return module {
    single<EventStreamAdapter>(named("taskRunnerEventStream")) {
      EventStreamAdapter(
        eventstream = get<Eventstream>(named("analyticsModuleEventStream")),
        ioDispatcher = get<kotlinx.coroutines.CoroutineDispatcher>(named("IO")),
        catalogName = "artifact_sync_task_runner",
      )
    }

    single {
      TaskRunnerService(
        eventStream = get(named("taskRunnerEventStream")),
        ioDispatcher = get<kotlinx.coroutines.CoroutineDispatcher>(named("IO")),
      )
    }
  }
}

/** Extension property to get TaskRunnerService from KoinApplication. */
val KoinApplication.taskRunnerService: TaskRunnerService
  get() = koin.get()
