package xyz.block.artifactswap.core.task_finder.di

import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import xyz.block.artifactswap.core.eventstream.EventStreamAdapter
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.task_finder.TaskFinderService

/** Configuration for TaskFinderService module. */
data class TaskFinderServiceConfig(val placeholder: Boolean = false)

/** Creates Koin modules for TaskFinderService. */
fun taskFinderServiceModules(
  application: KoinApplication,
  config: TaskFinderServiceConfig,
): Module {
  return module {
    single<EventStreamAdapter>(named("taskFinderEventStream")) {
      EventStreamAdapter(
        eventstream = get<Eventstream>(named("analyticsModuleEventStream")),
        ioDispatcher = get<kotlinx.coroutines.CoroutineDispatcher>(named("IO")),
        catalogName = "artifact_sync_task_finder",
      )
    }

    single {
      TaskFinderService(
        eventStream = get(named("taskFinderEventStream")),
        ioDispatcher = get<kotlinx.coroutines.CoroutineDispatcher>(named("IO")),
      )
    }
  }
}

/** Extension property to get TaskFinderService from KoinApplication. */
val KoinApplication.taskFinderService: TaskFinderService
  get() = koin.get()
