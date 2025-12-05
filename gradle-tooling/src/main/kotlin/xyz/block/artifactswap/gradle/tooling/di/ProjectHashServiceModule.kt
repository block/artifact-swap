package xyz.block.artifactswap.gradle.tooling.di

import java.nio.file.Path
import kotlinx.coroutines.CoroutineDispatcher
import org.gradle.tooling.GradleConnector
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.gradle.GradleProjectsProvider
import xyz.block.artifactswap.core.hashing.ProjectHashService
import xyz.block.artifactswap.core.hashing.services.HashingEventStream
import xyz.block.artifactswap.core.hashing.services.RealHashingEventStream
import xyz.block.artifactswap.gradle.tooling.GradleToolingHashingProjectsProvider

/** Configuration options for the project hash service module. */
data class ProjectHashServiceConfig(
  val placeholder: Boolean = false // Placeholder for future config
)

fun projectHashServiceModules(
  application: KoinApplication,
  config: ProjectHashServiceConfig,
): Module {
  return module {
    single<HashingEventStream> {
      RealHashingEventStream(
        eventstream = get<Eventstream>(),
        ioDispatcher = get<CoroutineDispatcher>(named("IO")),
      )
    }

    single<GradleProjectsProvider> {
      GradleToolingHashingProjectsProvider(
        GradleConnector.newCancellationTokenSource(),
        GradleConnector.newConnector()
          .forProjectDirectory(get<Path>(named("directory")).toFile())
          .useBuildDistribution()
          .connect(),
        gradleArgs = get(named("gradleArgs")),
        gradleJvmArgs = get(named("jvmArgs")),
      )
    }

    single<ProjectHashService> {
      ProjectHashService(
        gradleProjectsProvider = get<GradleProjectsProvider>(),
        eventStream = get(),
        ioDispatcher = get<CoroutineDispatcher>(named("IO")),
        defaultDispatcher = get<CoroutineDispatcher>(named("Default")),
      )
    }
  }
}

val KoinApplication.projectHashService: ProjectHashService
  get() = koin.get()
