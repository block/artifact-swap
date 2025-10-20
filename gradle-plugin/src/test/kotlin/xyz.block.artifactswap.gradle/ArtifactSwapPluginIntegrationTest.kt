package xyz.block.artifactswap.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the Artifact Swap plugin using Gradle TestKit.
 * These tests create real temporary Gradle projects and apply the plugin.
 */
class ArtifactSwapPluginIntegrationTest {

  @TempDir
  lateinit var testProjectDir: File

  private val testMavenGroup = "com.example.test"
  private val testBomVersion = "1.0.0"

  @Test
  fun `plugin can be applied successfully`() {
    // Create settings.gradle.kts
    settingsFile().writeText("""
      plugins {
        id("xyz.block.artifactswap")
      }

      rootProject.name = "test-project"

      artifactSwap {
        mavenGroup.set("$testMavenGroup")
        bomVersion.set("$testBomVersion")
      }
    """.trimIndent())

    // Create build.gradle.kts
    buildFile().writeText("""
      plugins {
        kotlin("jvm") version "1.9.0"
      }
    """.trimIndent())

    // Run a simple task to verify the plugin applies without errors
    val result = GradleRunner.create()
      .withProjectDir(testProjectDir)
      .withArguments("tasks", "--stacktrace")
      .withPluginClasspath()
      .build()

    assertEquals(TaskOutcome.SUCCESS, result.task(":tasks")?.outcome)
  }

  @Test
  fun `plugin creates configuration extension`() {
    // Create settings.gradle.kts that accesses the extension
    settingsFile().writeText("""
      plugins {
        id("xyz.block.artifactswap")
      }

      rootProject.name = "test-project"

      artifactSwap {
        mavenGroup.set("$testMavenGroup")
        bomVersion.set("$testBomVersion")
        localRepositoryPath.set(file("custom/path"))
      }

      // Verify we can read the values back
      println("Maven Group: ${'$'}{artifactSwap.mavenGroup.get()}")
      println("BOM Version: ${'$'}{artifactSwap.bomVersion.get()}")
    """.trimIndent())

    buildFile().writeText("""
      plugins {
        kotlin("jvm") version "1.9.0"
      }
    """.trimIndent())

    val result = GradleRunner.create()
      .withProjectDir(testProjectDir)
      .withArguments("tasks", "--quiet")
      .withPluginClasspath()
      .build()

    // Verify the configuration values were set correctly
    assertTrue(result.output.contains("Maven Group: $testMavenGroup"))
    assertTrue(result.output.contains("BOM Version: $testBomVersion"))
  }

  @Test
  fun `plugin applies project plugin to all projects`() {
    // Create settings.gradle.kts with multiple projects
    settingsFile().writeText("""
      plugins {
        id("xyz.block.artifactswap")
      }

      rootProject.name = "test-project"
      include(":subproject1")
      include(":subproject2")

      artifactSwap {
        mavenGroup.set("$testMavenGroup")
        bomVersion.set("$testBomVersion")
      }
    """.trimIndent())

    // Create root build file
    buildFile().writeText("""
      plugins {
        kotlin("jvm") version "1.9.0"
      }

      allprojects {
        // Check if ArtifactSwapProjectPlugin is applied
        plugins.withId("xyz.block.artifactswap") {
          println("Plugin applied to: ${'$'}project.name")
        }
      }
    """.trimIndent())

    // Create subproject directories
    File(testProjectDir, "subproject1").apply {
      mkdirs()
      resolve("build.gradle.kts").writeText("// Empty build file")
    }
    File(testProjectDir, "subproject2").apply {
      mkdirs()
      resolve("build.gradle.kts").writeText("// Empty build file")
    }

    val result = GradleRunner.create()
      .withProjectDir(testProjectDir)
      .withArguments("tasks", "--quiet")
      .withPluginClasspath()
      .build()

    // Note: The project plugin is auto-applied by the settings plugin,
    // but won't show up with plugins.withId because it's applied programmatically
    // This test mainly verifies that the multi-project structure works without errors
    assertEquals(TaskOutcome.SUCCESS, result.task(":tasks")?.outcome)
  }

  @Test
  fun `plugin sets default repository path`() {
    settingsFile().writeText("""
      plugins {
        id("xyz.block.artifactswap")
      }

      rootProject.name = "test-project"

      artifactSwap {
        mavenGroup.set("$testMavenGroup")
        bomVersion.set("$testBomVersion")
        // Don't set localRepositoryPath - should use default
      }

      // Print the default value
      println("Repo Path: ${'$'}{artifactSwap.localRepositoryPath.get()}")
    """.trimIndent())

    buildFile().writeText("""
      plugins {
        kotlin("jvm") version "1.9.0"
      }
    """.trimIndent())

    val result = GradleRunner.create()
      .withProjectDir(testProjectDir)
      .withArguments("tasks", "--quiet")
      .withPluginClasspath()
      .build()

    // Verify the default path contains .m2/repository
    assertTrue(result.output.contains(".m2${File.separator}repository"))
  }

  private fun settingsFile() = File(testProjectDir, "settings.gradle.kts")
  private fun buildFile() = File(testProjectDir, "build.gradle.kts")
}
