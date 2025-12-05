package xyz.block.artifactswap.gradle.tooling

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.gradle.tooling.GradleConnector
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class GradleToolingHashingProjectsProviderTest {

  @TempDir lateinit var tempDir: Path

  @Test
  fun `getProjectHashingInfos filters out root project`() = runTest {
    val projectDir = tempDir.resolve("sample-project").also { it.createDirectories() }
    createMultiModuleProject(projectDir)

    val connector =
      GradleConnector.newConnector().forProjectDirectory(projectDir.toFile()).useBuildDistribution()
    val cancellationTokenSource = GradleConnector.newCancellationTokenSource()
    val connection = connector.connect()
    val provider =
      GradleToolingHashingProjectsProvider(
        cancellationTokenSource = cancellationTokenSource,
        projectConnection = connection,
        gradleArgs = emptyList(),
        gradleJvmArgs = emptyList(),
      )

    try {
      val hashingInfos = provider.getProjectHashingInfos().getOrThrow()
      assertFalse(hashingInfos.any { it.projectPath == ":" })
    } finally {
      provider.cleanup()
    }
  }

  @Test
  fun `getProjectHashingInfos excludes projects with settings gradle`() = runTest {
    val projectDir = tempDir.resolve("sample-project").also { it.createDirectories() }
    projectDir
      .resolve("settings.gradle")
      .writeText(
        """
        rootProject.name = 'sample-project'
        include(':app', ':projectThatHasSettingsFile')
        """
          .trimIndent()
      )
    projectDir
      .resolve("build.gradle")
      .writeText(
        """
        plugins { id 'base' }
        """
          .trimIndent()
      )

    // Create regular module
    createAppModule(projectDir.resolve("app"))

    // Create projectThatHasSettingsFile with its own settings.gradle (should be excluded)
    val projectThatHasSettingsFileDir = projectDir.resolve("projectThatHasSettingsFile")
    projectThatHasSettingsFileDir.createDirectories()
    projectThatHasSettingsFileDir
      .resolve("build.gradle")
      .writeText(
        """
        plugins { id 'groovy-gradle-plugin' }
        """
          .trimIndent()
      )
    projectThatHasSettingsFileDir
      .resolve("settings.gradle")
      .writeText(
        """
        rootProject.name = 'projectThatHasSettingsFile'
        """
          .trimIndent()
      )
    projectThatHasSettingsFileDir.resolve("src/main/groovy/MyPlugin.groovy").also { path ->
      path.parent.createDirectories()
      path.writeText("class MyPlugin {}")
    }

    val connector =
      GradleConnector.newConnector().forProjectDirectory(projectDir.toFile()).useBuildDistribution()
    val cancellationTokenSource = GradleConnector.newCancellationTokenSource()
    val connection = connector.connect()
    val provider =
      GradleToolingHashingProjectsProvider(
        cancellationTokenSource = cancellationTokenSource,
        projectConnection = connection,
        gradleArgs = emptyList(),
        gradleJvmArgs = emptyList(),
      )

    try {
      val hashingInfos = provider.getProjectHashingInfos().getOrThrow()
      assertTrue(hashingInfos.any { it.projectPath == ":app" })
      assertFalse(hashingInfos.any { it.projectPath == ":projectThatHasSettingsFile" })
    } finally {
      provider.cleanup()
    }
  }

  @Test
  fun `getProjectHashingInfos filters yml and txt files`() = runTest {
    val projectDir = tempDir.resolve("sample-project").also { it.createDirectories() }
    projectDir
      .resolve("settings.gradle")
      .writeText(
        """
        rootProject.name = 'sample-project'
        include(':app')
        """
          .trimIndent()
      )
    projectDir
      .resolve("build.gradle")
      .writeText(
        """
        plugins { id 'base' }
        """
          .trimIndent()
      )

    val appDir = projectDir.resolve("app")
    appDir.createDirectories()
    appDir
      .resolve("build.gradle")
      .writeText(
        """
        plugins { id 'java' }
        """
          .trimIndent()
      )
    appDir.resolve("src/main/java/App.java").also { path ->
      path.parent.createDirectories()
      path.writeText("public class App {}")
    }
    appDir.resolve("config.yml").writeText("key: value")
    appDir.resolve("notes.txt").writeText("some notes")
    appDir.resolve("data.json").writeText("{}")

    val connector =
      GradleConnector.newConnector().forProjectDirectory(projectDir.toFile()).useBuildDistribution()
    val cancellationTokenSource = GradleConnector.newCancellationTokenSource()
    val connection = connector.connect()
    val provider =
      GradleToolingHashingProjectsProvider(
        cancellationTokenSource = cancellationTokenSource,
        projectConnection = connection,
        gradleArgs = emptyList(),
        gradleJvmArgs = emptyList(),
      )

    try {
      val hashingInfos = provider.getProjectHashingInfos().getOrThrow()
      val appInfo = hashingInfos.single { it.projectPath == ":app" }
      val appFiles =
        appInfo.filesToHash
          .map { appInfo.projectDirectory.relativize(it).pathString.replace('\\', '/') }
          .toList()

      assertTrue(appFiles.contains("build.gradle"))
      assertTrue(appFiles.contains("src/main/java/App.java"))
      assertTrue(appFiles.contains("data.json"))
      assertFalse(appFiles.any { it.endsWith(".yml") })
      assertFalse(appFiles.any { it.endsWith(".txt") })
    } finally {
      provider.cleanup()
    }
  }

  @Test
  fun `getProjectHashingInfos handles empty project with no source files`() = runTest {
    val projectDir = tempDir.resolve("sample-project").also { it.createDirectories() }
    projectDir
      .resolve("settings.gradle")
      .writeText(
        """
        rootProject.name = 'sample-project'
        include(':empty')
        """
          .trimIndent()
      )
    projectDir
      .resolve("build.gradle")
      .writeText(
        """
        plugins { id 'base' }
        """
          .trimIndent()
      )

    val emptyDir = projectDir.resolve("empty")
    emptyDir.createDirectories()
    emptyDir
      .resolve("build.gradle")
      .writeText(
        """
        plugins { id 'java' }
        """
          .trimIndent()
      )

    val connector =
      GradleConnector.newConnector().forProjectDirectory(projectDir.toFile()).useBuildDistribution()
    val cancellationTokenSource = GradleConnector.newCancellationTokenSource()
    val connection = connector.connect()
    val provider =
      GradleToolingHashingProjectsProvider(
        cancellationTokenSource = cancellationTokenSource,
        projectConnection = connection,
        gradleArgs = emptyList(),
        gradleJvmArgs = emptyList(),
      )

    try {
      val hashingInfos = provider.getProjectHashingInfos().getOrThrow()
      val emptyInfo = hashingInfos.single { it.projectPath == ":empty" }
      val emptyFiles = emptyInfo.filesToHash.toList()

      assertEquals(1, emptyFiles.size)
      assertTrue(emptyFiles.first().fileName.toString() == "build.gradle")
    } finally {
      provider.cleanup()
    }
  }

  @Test
  fun `cleanup closes connection and cancels token`(@TempDir tempDir: Path) = runTest {
    val projectDir = tempDir.resolve("sample-project").also { it.createDirectories() }
    projectDir
      .resolve("settings.gradle")
      .writeText(
        """
        rootProject.name = 'sample-project'
        """
          .trimIndent()
      )
    projectDir
      .resolve("build.gradle")
      .writeText(
        """
        plugins { id 'base' }
        """
          .trimIndent()
      )

    val connector =
      GradleConnector.newConnector().forProjectDirectory(projectDir.toFile()).useBuildDistribution()
    val cancellationTokenSource = GradleConnector.newCancellationTokenSource()
    val connection = connector.connect()
    val provider =
      GradleToolingHashingProjectsProvider(
        cancellationTokenSource = cancellationTokenSource,
        projectConnection = connection,
        gradleArgs = emptyList(),
        gradleJvmArgs = emptyList(),
      )

    provider.cleanup()

    // Verify token is cancelled by checking isCancellationRequested
    assertTrue(cancellationTokenSource.token().isCancellationRequested)
  }

  @Test
  fun `getProjectHashingInfos returns files in sorted order`() = runTest {
    val projectDir = tempDir.resolve("sample-project").also { it.createDirectories() }
    projectDir
      .resolve("settings.gradle")
      .writeText(
        """
        rootProject.name = 'sample-project'
        include(':app')
        """
          .trimIndent()
      )
    projectDir
      .resolve("build.gradle")
      .writeText(
        """
        plugins { id 'base' }
        """
          .trimIndent()
      )

    val appDir = projectDir.resolve("app")
    appDir.createDirectories()
    appDir
      .resolve("build.gradle")
      .writeText(
        """
        plugins { id 'java' }
        """
          .trimIndent()
      )
    // Create files in non-alphabetical order
    appDir.resolve("zebra.java").writeText("class Zebra {}")
    appDir.resolve("alpha.java").writeText("class Alpha {}")
    appDir.resolve("middle.java").writeText("class Middle {}")

    val connector =
      GradleConnector.newConnector().forProjectDirectory(projectDir.toFile()).useBuildDistribution()
    val cancellationTokenSource = GradleConnector.newCancellationTokenSource()
    val connection = connector.connect()
    val provider =
      GradleToolingHashingProjectsProvider(
        cancellationTokenSource = cancellationTokenSource,
        projectConnection = connection,
        gradleArgs = emptyList(),
        gradleJvmArgs = emptyList(),
      )

    try {
      val hashingInfos = provider.getProjectHashingInfos().getOrThrow()
      val appInfo = hashingInfos.single { it.projectPath == ":app" }
      val appFiles = appInfo.filesToHash.map { it.fileName.toString() }.toList()

      // Files should be sorted
      val sortedFiles = appFiles.sorted()
      assertEquals(sortedFiles, appFiles)
    } finally {
      provider.cleanup()
    }
  }

  @Test
  fun `getProjectHashingInfos returns expected projects and files`() = runTest {
    val projectDir = tempDir.resolve("sample-project").also { it.createDirectories() }
    createMultiModuleProject(projectDir)

    val connector =
      GradleConnector.newConnector().forProjectDirectory(projectDir.toFile()).useBuildDistribution()
    val cancellationTokenSource = GradleConnector.newCancellationTokenSource()
    val connection = connector.connect()
    val provider =
      GradleToolingHashingProjectsProvider(
        cancellationTokenSource = cancellationTokenSource,
        projectConnection = connection,
        gradleArgs = emptyList(),
        gradleJvmArgs = emptyList(),
      )

    try {
      val hashingInfos = provider.getProjectHashingInfos().getOrThrow()
      assertTrue(hashingInfos.any { it.projectPath == ":app" })
      assertTrue(hashingInfos.any { it.projectPath == ":lib" })
      assertFalse(hashingInfos.any { it.projectPath == ":" })

      val appInfo = hashingInfos.single { it.projectPath == ":app" }
      assertEquals(projectDir.resolve("app").toRealPath(), appInfo.projectDirectory.toRealPath())
      val appFiles =
        appInfo.filesToHash
          .map { appInfo.projectDirectory.relativize(it).pathString.replace('\\', '/') }
          .toList()
      assertEquals(listOf("build.gradle", "src/main/java/com/example/app/App.java"), appFiles)

      val libInfo = hashingInfos.single { it.projectPath == ":lib" }
      assertEquals(projectDir.resolve("lib").toRealPath(), libInfo.projectDirectory.toRealPath())
      val libFiles =
        libInfo.filesToHash
          .map { libInfo.projectDirectory.relativize(it).pathString.replace('\\', '/') }
          .toList()
      assertEquals(
        listOf(
          "build.gradle",
          "src/main/kotlin/com/example/lib/Library.kt",
          "src/main/resources/library.properties",
        ),
        libFiles,
      )
    } finally {
      provider.cleanup()
    }
  }

  private fun createMultiModuleProject(projectDir: Path) {
    projectDir
      .resolve("settings.gradle")
      .writeText(
        """
        rootProject.name = 'sample-project'
        include(':app', ':lib')
        """
          .trimIndent()
      )
    projectDir
      .resolve("build.gradle")
      .writeText(
        """
        plugins { id 'base' }
        """
          .trimIndent()
      )

    createAppModule(projectDir.resolve("app"))
    createLibModule(projectDir.resolve("lib"))
  }

  private fun createAppModule(moduleDir: Path) {
    moduleDir.createDirectories()
    moduleDir
      .resolve("build.gradle")
      .writeText(
        """
        plugins { id 'java' }
        """
          .trimIndent()
      )

    // Files that should be hashed
    moduleDir.resolve("src/main/java/com/example/app").createDirectories()
    moduleDir
      .resolve("src/main/java/com/example/app/App.java")
      .writeText(
        """
        package com.example.app;

        public class App {}
        """
          .trimIndent()
      )

    // Files that should be ignored
    moduleDir.resolve("build/intermediates/ignored.txt").also { path ->
      path.parent.createDirectories()
      path.writeText("ignore")
    }
    moduleDir.resolve("src/test/java/com/example/app/AppTest.java").also { path ->
      path.parent.createDirectories()
      path.writeText(
        """
        package com.example.app;

        public class AppTest {}
        """
          .trimIndent()
      )
    }
    moduleDir.resolve("src/androidTest/java/com/example/app/AppAndroidTest.java").also { path ->
      path.parent.createDirectories()
      path.writeText(
        """
        package com.example.app;

        public class AppAndroidTest {}
        """
          .trimIndent()
      )
    }
    moduleDir.resolve("README.md").writeText("ignored document")
    moduleDir.resolve("config.yaml").writeText("ignored: true")
  }

  private fun createLibModule(moduleDir: Path) {
    moduleDir.createDirectories()
    moduleDir
      .resolve("build.gradle")
      .writeText(
        """
        plugins {
            id 'java-library'
        }
        """
          .trimIndent()
      )

    moduleDir.resolve("src/main/kotlin/com/example/lib").createDirectories()
    moduleDir
      .resolve("src/main/kotlin/com/example/lib/Library.kt")
      .writeText(
        """
        package com.example.lib

        class Library
        """
          .trimIndent()
      )
    moduleDir.resolve("src/main/resources").createDirectories()
    moduleDir.resolve("src/main/resources/library.properties").writeText("library=true")

    moduleDir.resolve("notes.txt").writeText("should be ignored")
  }
}
