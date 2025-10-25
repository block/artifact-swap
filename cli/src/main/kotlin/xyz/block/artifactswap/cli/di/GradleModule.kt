package xyz.block.artifactswap.cli.di

import org.gradle.tooling.CancellationTokenSource
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import org.koin.dsl.module
import xyz.block.artifactswap.cli.options.GradleOptions
import java.nio.file.Path

internal fun gradleModule(
    gradleOptions: GradleOptions
) = module {
    factory<GradleConnector> {
        GradleConnector.newConnector()
    }

    factory<ProjectConnection> {
        get<GradleConnector>()
            .forProjectDirectory(get<Path>(named("directory")).toFile())
            .useBuildDistribution()
            .connect()
    }

    factory<CancellationTokenSource> {
        GradleConnector.newCancellationTokenSource()
    }

    single(named("logGradle")) { gradleOptions.logGradle }

    single(named("gradleArgs")) { gradleOptions.gradleArgs }

    single(named("jvmArgs")) {
        buildList {
            gradleOptions.maxGradleMemory?.let { add("-Xmx${it}M") }
            addAll(gradleOptions.gradleJvmArgs)
        }
    }
}

fun KoinApplication.newProjectConnection(): ProjectConnection {
    return koin.get()
}

fun KoinApplication.newCancellationTokenSource(): CancellationTokenSource {
    return koin.get()
}

val KoinApplication.logGradle: Boolean
    get() = koin.get(named("logGradle"))

val KoinApplication.gradleArgs: List<String>
    get() = koin.get(named("gradleArgs"))

val KoinApplication.gradleJvmArgs: List<String>
    get() = koin.get(named("jvmArgs"))
