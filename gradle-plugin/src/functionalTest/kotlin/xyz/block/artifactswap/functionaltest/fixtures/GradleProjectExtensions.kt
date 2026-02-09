package xyz.block.artifactswap.functionaltest.fixtures

import com.autonomousapps.kit.GradleBuilder
import com.autonomousapps.kit.GradleProject
import java.io.File
import org.gradle.testkit.runner.BuildResult
import org.gradle.util.GradleVersion

/** Gets the Gradle version to use for tests from system property, or current version. */
private val testGradleVersion: GradleVersion
  get() {
    val versionString = System.getProperty("gradleVersion")
    return if (versionString.isNullOrBlank()) {
      GradleVersion.current()
    } else {
      GradleVersion.version(versionString)
    }
  }

/** Builds a Gradle project with the specified arguments. */
fun GradleProject.build(vararg args: String): BuildResult =
  GradleBuilder.build(testGradleVersion, rootDir, *args, "--stacktrace")

/** Builds a Gradle project expecting failure. */
fun GradleProject.buildAndFail(vararg args: String): BuildResult =
  GradleBuilder.buildAndFail(testGradleVersion, rootDir, *args, "--stacktrace")

/**
 * Simulates an IDE sync by running with -Didea.sync.active=true. This is how we test artifact swap
 * behavior during sync.
 */
fun GradleProject.ideSync(vararg additionalArgs: String): BuildResult {
  val args =
    arrayOf("help", "-Didea.sync.active=true", "--no-configuration-cache", "--stacktrace") +
      additionalArgs
  return GradleBuilder.build(testGradleVersion, rootDir, *args)
}

/** Simulates an IDE sync expecting failure. */
fun GradleProject.ideSyncAndFail(vararg additionalArgs: String): BuildResult {
  val args =
    arrayOf("help", "-Didea.sync.active=true", "--no-configuration-cache", "--stacktrace") +
      additionalArgs
  return GradleBuilder.buildAndFail(testGradleVersion, rootDir, *args)
}

/** Writes a file to the project directory. */
fun GradleProject.writeFile(path: String, content: String) {
  val file = File(rootDir, path)
  file.parentFile.mkdirs()
  file.writeText(content)
}

/** Creates a directory in the project. */
fun GradleProject.createDir(path: String) {
  File(rootDir, path).mkdirs()
}

/** Reads a file from the project directory. */
fun GradleProject.readFile(path: String): String {
  return File(rootDir, path).readText()
}

/** Checks if a file exists in the project. */
fun GradleProject.fileExists(path: String): Boolean {
  return File(rootDir, path).exists()
}
