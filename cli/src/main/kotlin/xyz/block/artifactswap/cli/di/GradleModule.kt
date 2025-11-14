package xyz.block.artifactswap.cli.di

import java.io.File
import java.nio.file.Path
import java.util.Properties
import org.gradle.tooling.CancellationTokenSource
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import org.koin.dsl.module
import xyz.block.artifactswap.cli.options.GradleOptions

internal fun gradleModule(gradleOptions: GradleOptions) = module {
  // Gradle properties loaded from local and global gradle.properties files
  // Global properties (~/.gradle/gradle.properties) take precedence over local
  // (./gradle.properties)
  single<Properties>(named("gradleProperties")) {
    val properties = Properties()

    // Load local gradle.properties from the project directory (--dir parameter or current
    // directory)
    val projectDirectory = get<Path>(named("directory"))
    val localGradlePropertiesFile = projectDirectory.resolve("gradle.properties").toFile()
    if (localGradlePropertiesFile.exists()) {
      localGradlePropertiesFile.inputStream().use { properties.load(it) }
    }

    // Load global gradle.properties (overrides local)
    // Use GRADLE_USER_HOME if set, otherwise fall back to ~/.gradle
    val gradleUserHome =
      System.getenv("GRADLE_USER_HOME")
        ?: File(System.getProperty("user.home"), ".gradle").absolutePath
    val globalGradlePropertiesFile = File(gradleUserHome, "gradle.properties")
    if (globalGradlePropertiesFile.exists()) {
      globalGradlePropertiesFile.inputStream().use { properties.load(it) }
    }

    // Add system properties and environment variables as fallback
    System.getProperties().forEach { key, value ->
      if (!properties.containsKey(key)) {
        properties[key] = value
      }
    }

    properties
  }

  factory<GradleConnector> { GradleConnector.newConnector() }

  factory<ProjectConnection> {
    get<GradleConnector>()
      .forProjectDirectory(get<Path>(named("directory")).toFile())
      .useBuildDistribution()
      .connect()
  }

  factory<CancellationTokenSource> { GradleConnector.newCancellationTokenSource() }

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

val KoinApplication.gradleProperties: Properties
  get() = koin.get(named("gradleProperties"))
