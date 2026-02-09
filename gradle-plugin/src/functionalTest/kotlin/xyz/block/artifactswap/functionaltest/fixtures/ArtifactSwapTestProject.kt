package xyz.block.artifactswap.functionaltest.fixtures

import com.autonomousapps.kit.AbstractGradleProject
import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.Source
import com.autonomousapps.kit.gradle.Dependency.Companion.implementation
import com.autonomousapps.kit.gradle.GradleProperties
import com.autonomousapps.kit.gradle.Plugin
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList
import java.io.File
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createTempDirectory
import org.gradle.testkit.runner.BuildResult

/**
 * Helper for creating test projects with artifact swap configured.
 *
 * @param mavenLocalPath Path to the local maven repository for test artifacts. Use a JUnit
 *   `@TempDir` to ensure automatic cleanup.
 * @param mavenGroup Unique maven group for test isolation. Defaults to a UUID-based group.
 */
class ArtifactSwapTestProject(
  private val mavenLocalPath: Path,
  val mavenGroup: String = "com.test.artifacts.${UUID.randomUUID()}",
) : AbstractGradleProject() {

  companion object {

    // Regex to extract include statements from settings.gradle
    private val INCLUDE_PATTERN = Regex("include[(\\s]+?[\"'](\\S+)[\"']")
  }

  private fun gradleProperties() =
    GradleProperties.of(
      "artifactswap.enabled=true",
      "artifactswap.primaryArtifactsMavenGroup=$mavenGroup",
      "artifactswap.artifactoryBaseUrl=https://example.com/artifactory/",
      "artifactswap.primaryRepositoryName=test-repo",
      "artifactswap.bomArtifactId=bom",
      "artifactswap.eventstreamBaseUrl=https://example.com/eventstream/",
      "artifactswap.artifactoryPublisherTokenFileName=test-token.txt",
      "artifactswap.bomSourceBranchName=main",
      "artifactswap.mavenLocalDirectory=$mavenLocalPath",
      "org.gradle.caching=false",
      "org.gradle.configuration-cache=false",
    )

  /** Creates a basic multi-module project with artifact swap configured. */
  fun createBasicProject(
    dslKind: GradleProject.DslKind = GradleProject.DslKind.GROOVY
  ): GradleProject =
    newGradleProjectBuilder(dslKind)
      .withRootProject {
        gradleProperties = gradleProperties()

        withSettingsScript {
          plugins(
            Plugin("com.gradle.develocity", "4.3.1"),
            Plugin("com.fueledbycaffeine.spotlight", "1.6.6", apply = false),
            Plugin("xyz.block.artifactswap.settings", PLUGIN_UNDER_TEST_VERSION),
          )

          additions =
            """
            enableFeaturePreview('TYPESAFE_PROJECT_ACCESSORS')

            develocity {
              buildScan {
                publishing.onlyIf { false }
              }
            }
            """
              .trimIndent()
        }
      }
      .withSubproject("lib") {
        withBuildScript { plugins(Plugin("java-library")) }
        sources =
          mutableListOf(
            Source.java(
                """
                package com.test.lib;

                public class LibClass {
                  public void hello() {
                    System.out.println("Hello from lib");
                  }
                }
                """
                  .trimIndent()
              )
              .withPath("com.test.lib", "LibClass")
              .build()
          )
      }
      .withSubproject("app") {
        withBuildScript {
          plugins(Plugin("java-library"))
          dependencies(implementation(":lib"))
        }
        sources =
          mutableListOf(
            Source.java(
                """
                package com.test.app;

                import com.test.lib.LibClass;

                public class AppClass {
                  public void run() {
                    new LibClass().hello();
                  }
                }
                """
                  .trimIndent()
              )
              .withPath("com.test.app", "AppClass")
              .build()
          )
      }
      .write()
      .also { project ->
        // Convert testkit's include statements to Spotlight's all-projects.txt
        convertIncludesToAllProjectsFile(project, dslKind)
      }

  /**
   * Creates a project with SQLDelight configuration for testing. This focuses on testing the
   * dependency DSL, not actual SQLDelight functionality.
   */
  fun createSqlDelightProject(
    dslKind: GradleProject.DslKind = GradleProject.DslKind.GROOVY
  ): GradleProject =
    newGradleProjectBuilder(dslKind)
      .withRootProject {
        gradleProperties = gradleProperties()

        withSettingsScript {
          plugins(
            Plugin("com.gradle.develocity", "4.3.1"),
            Plugin("com.fueledbycaffeine.spotlight", "1.6.6", apply = false),
            Plugin("xyz.block.artifactswap.settings", PLUGIN_UNDER_TEST_VERSION),
          )

          additions =
            """
            enableFeaturePreview('TYPESAFE_PROJECT_ACCESSORS')

            develocity {
              buildScan {
                publishing.onlyIf { false }
              }
            }
            """
              .trimIndent()
        }
      }
      .withSubproject("db") {
        withBuildScript {
          additions =
            """
            plugins {
              id 'org.jetbrains.kotlin.jvm' version '2.1.0'
              id 'java-library'
              id 'app.cash.sqldelight' version '2.0.2'
            }

            sqldelight {
              databases {
                // Define multiple databases so consumer can reference them
                TestDatabase {
                  packageName = "com.test.db"
                }
                ConsumerDatabase {
                  packageName = "com.test.db.consumer"
                }
              }
            }
            """
              .trimIndent()
        }
        sources = mutableListOf()
      }
      .withSubproject("consumer") {
        withBuildScript {
          additions =
            """
            plugins {
              id 'org.jetbrains.kotlin.jvm' version '2.1.0'
              id 'java-library'
              id 'app.cash.sqldelight' version '2.0.2'
            }

            sqldelight {
              databases {
                ConsumerDatabase {
                  packageName = "com.test.consumer"
                  // This is the critical test - using project accessor to reference db module
                  // SQLDelight will look for TestDatabase in projects.db
                  dependency projects.db
                }
              }
            }
            """
              .trimIndent()
        }
        sources = mutableListOf()
      }
      .write()
      .also { project -> convertIncludesToAllProjectsFile(project, dslKind) }

  /** Creates a project with dependency-analysis plugin configured for testing. */
  fun createDagpProject(
    dslKind: GradleProject.DslKind = GradleProject.DslKind.GROOVY
  ): GradleProject =
    newGradleProjectBuilder(dslKind)
      .withRootProject {
        gradleProperties = gradleProperties()

        withSettingsScript {
          plugins(
            Plugin("com.gradle.develocity", "4.3.1"),
            Plugin("com.fueledbycaffeine.spotlight", "1.6.6", apply = false),
            Plugin("xyz.block.artifactswap.settings", PLUGIN_UNDER_TEST_VERSION),
          )

          additions =
            """
            enableFeaturePreview('TYPESAFE_PROJECT_ACCESSORS')

            develocity {
              buildScan {
                publishing.onlyIf { false }
              }
            }
            """
              .trimIndent()
        }

        withBuildScript {
          // Apply DAGP to root - it will analyze all projects
          plugins(Plugin("com.autonomousapps.dependency-analysis", "1.32.0"))
        }
      }
      .withSubproject("common-ui") {
        withBuildScript { plugins(Plugin("java-library")) }
        sources =
          mutableListOf(
            Source.java(
                """
                package com.test.ui;

                public class UiComponent {
                  public void render() {}
                }
                """
                  .trimIndent()
              )
              .withPath("com.test.ui", "UiComponent")
              .build()
          )
      }
      .withSubproject("app") {
        withBuildScript {
          plugins(Plugin("java-library"))
          dependencies(implementation(":common-ui"))

          additions =
            """
            dependencyAnalysis {
              issues {
                onAny {
                  severity('ignore')
                  // Test that project dependencies work (our fix ensures project() returns real deps)
                  exclude(':common-ui')
                }
              }
            }
            """
              .trimIndent()
        }
        sources =
          mutableListOf(
            Source.java(
                """
                package com.test.app;

                import com.test.ui.UiComponent;

                public class App {
                  public void run() {
                    new UiComponent().render();
                  }
                }
                """
                  .trimIndent()
              )
              .withPath("com.test.app", "App")
              .build()
          )
      }
      .write()
      .also { project -> convertIncludesToAllProjectsFile(project, dslKind) }

  /**
   * Converts testkit's auto-generated include statements to Spotlight's all-projects.txt file. This
   * is required because artifact-swap uses Spotlight which reads projects from all-projects.txt,
   * not from include statements.
   */
  private fun convertIncludesToAllProjectsFile(
    project: GradleProject,
    dslKind: GradleProject.DslKind,
  ) {
    val settingsFile = project.rootDir.resolve(dslKind.settingsFile)
    val settingsContents = settingsFile.readText()

    // Extract all include statements
    val projectPaths =
      INCLUDE_PATTERN.findAll(settingsContents)
        .map {
          val (path) = it.destructured
          path
        }
        .toList()

    // Remove all include statements
    // Note: Don't modify the maven repository URLs - testkit populates them correctly
    val strippedSettings =
      settingsContents
        .lines()
        .filterNot { it.matches("include[( ][\"'].*[\"']\\)?".toRegex()) }
        .joinToString("\n")

    settingsFile.writeText(strippedSettings)

    // Write projects to all-projects.txt and ide-projects.txt (Spotlight format)
    project.rootDir.resolve("gradle").mkdirs()
    project.rootDir
      .resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
      .writeText(projectPaths.joinToString("\n"))
    // Create empty ide-projects.txt (Spotlight will use all-projects.txt as fallback)
    project.rootDir.resolve(SpotlightProjectList.IDE_PROJECTS_LOCATION).writeText("")
  }

  /**
   * Initializes a git repository in the test project with origin/main branch. This is required for
   * artifact-swap to determine the BOM version. Creates a unique remote repository in a temp
   * directory to avoid conflicts between tests.
   */
  fun initializeGitRepo(project: GradleProject) {
    // Create a unique temp directory for the remote (one per test).
    // deleteOnExit ensures cleanup even if the test doesn't explicitly clean up.
    val remoteTempDir = createTempDirectory("artifact-swap-test-remote-").toFile()
    remoteTempDir.deleteOnExit()
    val remoteRepoPath = File(remoteTempDir, "remote.git").absolutePath

    val commands =
      listOf(
        listOf("git", "init", "-b", "main"),
        listOf("git", "config", "user.name", "Test User"),
        listOf("git", "config", "user.email", "test@example.com"),
        listOf("git", "add", "."),
        listOf("git", "commit", "-m", "Initial commit"),
        // Create a bare repo in unique temp dir to act as remote
        listOf("git", "init", "--bare", remoteRepoPath),
        listOf("git", "remote", "add", "origin", remoteRepoPath),
        listOf("git", "push", "-u", "origin", "main"),
      )

    commands.forEach { command ->
      val process =
        ProcessBuilder(command).directory(project.rootDir).redirectErrorStream(true).start()
      // Read output before waitFor() to avoid blocking if the output buffer fills
      val output = process.inputStream.bufferedReader().readText()
      val exitCode = process.waitFor()
      if (exitCode != 0) {
        throw RuntimeException("Git command failed: ${command.joinToString(" ")}\n$output")
      }
    }
  }

  /** Sets up the always-keep list for projects that should never be swapped. */
  fun setupAlwaysKeepList(project: GradleProject, projectPaths: List<String>) {
    project.writeFile("gradle/artifact-swap-always-keep.txt", projectPaths.joinToString("\n"))
  }

  /**
   * Sets up the IDE projects list to specify which modules are included in IDE sync. Modules NOT in
   * this list (but in all-projects.txt) can be swapped to artifacts.
   *
   * @param project The GradleProject to configure
   * @param projectPaths List of project paths to include in IDE sync (e.g., [":app"])
   */
  fun setupIdeProjectsList(project: GradleProject, projectPaths: List<String>) {
    project.writeFile(SpotlightProjectList.IDE_PROJECTS_LOCATION, projectPaths.joinToString("\n"))
  }

  /**
   * Gets the current git commit SHA from the project.
   *
   * @param project The GradleProject to get the commit SHA from
   * @return The current HEAD commit SHA
   * @throws IllegalStateException if git repo is not initialized
   */
  fun getGitCommitSha(project: GradleProject): String {
    val process =
      ProcessBuilder("git", "rev-parse", "HEAD")
        .directory(project.rootDir)
        .redirectErrorStream(true)
        .start()
    val commitSha = process.inputStream.bufferedReader().readText().trim()
    process.waitFor()

    if (commitSha.isEmpty()) {
      throw IllegalStateException(
        "Could not get git commit SHA. Make sure initializeGitRepo() was called first."
      )
    }
    return commitSha
  }

  /**
   * Runs Gradle to publish artifacts using the artifact swap publish plugin. This exercises the
   * real publishing workflow instead of creating fake artifacts.
   *
   * @param project The GradleProject to publish from
   * @param modules List of module paths to publish (e.g., [":lib", ":app"])
   * @return The BuildResult from the Gradle execution
   */
  fun runGradlePublish(project: GradleProject, modules: List<String>): BuildResult {
    // Build publish tasks for each module - uses standard maven-publish task naming
    val publishTasks =
      modules.map { module -> "${module}:publishMavenPublicationToArtifactSwapLocalRepository" }
    return project.build(*publishTasks.toTypedArray())
  }

  /**
   * Publishes a single artifact with a specific version (content hash). This is used by E2E tests
   * where the BOM publisher expects artifacts to be versioned by their content hash.
   *
   * @param project The GradleProject to publish from
   * @param modulePath The module path (e.g., ":lib")
   * @param version The version to publish with (typically a content hash)
   */
  fun publishArtifactWithVersion(project: GradleProject, modulePath: String, version: String) {
    val artifactId = modulePath.removePrefix(":").replace(":", "_")
    val groupPath = mavenGroup.replace(".", "/")
    val artifactDir = mavenLocalPath.resolve(groupPath).resolve(artifactId).resolve(version)

    artifactDir.toFile().mkdirs()

    // Create a minimal POM
    val pomFile = artifactDir.resolve("$artifactId-$version.pom").toFile()
    pomFile.writeText(
      """<?xml version="1.0" encoding="UTF-8"?>
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>$mavenGroup</groupId>
  <artifactId>$artifactId</artifactId>
  <version>$version</version>
  <name>$artifactId</name>
</project>"""
    )

    // Create a minimal JAR (required for artifact validation)
    val jarFile = artifactDir.resolve("$artifactId-$version.jar").toFile()
    jarFile.writeBytes(byteArrayOf())

    // Create sources JAR (required for artifact validation)
    val sourcesJarFile = artifactDir.resolve("$artifactId-$version-sources.jar").toFile()
    sourcesJarFile.writeBytes(byteArrayOf())

    // Create module file (required for artifact validation)
    val moduleFile = artifactDir.resolve("$artifactId-$version.module").toFile()
    moduleFile.writeText("{}")
  }

  /**
   * Creates a project configured for E2E testing with standard maven-publish configured. This
   * project can be used with actual Gradle publishing tasks to local maven.
   *
   * Uses standard maven-publish plugin rather than artifact swap publish plugin to avoid AGP class
   * loading issues in JVM-only test projects.
   */
  fun createPublishableProject(
    dslKind: GradleProject.DslKind = GradleProject.DslKind.GROOVY
  ): GradleProject =
    newGradleProjectBuilder(dslKind)
      .withRootProject {
        gradleProperties = gradlePropertiesForPublishing()

        withSettingsScript {
          plugins(
            Plugin("com.gradle.develocity", "4.3.1"),
            Plugin("com.fueledbycaffeine.spotlight", "1.6.6", apply = false),
            Plugin("xyz.block.artifactswap.settings", PLUGIN_UNDER_TEST_VERSION),
          )

          additions =
            """
            enableFeaturePreview('TYPESAFE_PROJECT_ACCESSORS')

            develocity {
              buildScan {
                publishing.onlyIf { false }
              }
            }
            """
              .trimIndent()
        }
      }
      .withSubproject("lib") {
        withBuildScript {
          plugins(Plugin("java-library"))
          // Configure maven-publish manually for E2E tests
          additions = mavenPublishConfig("lib")
        }
        sources =
          mutableListOf(
            Source.java(
                """
                package com.test.lib;

                public class LibClass {
                  public void hello() {
                    System.out.println("Hello from lib");
                  }
                }
                """
                  .trimIndent()
              )
              .withPath("com.test.lib", "LibClass")
              .build()
          )
      }
      .withSubproject("app") {
        withBuildScript {
          plugins(Plugin("java-library"))
          dependencies(implementation(":lib"))
          // Configure maven-publish manually for E2E tests
          additions = mavenPublishConfig("app")
        }
        sources =
          mutableListOf(
            Source.java(
                """
                package com.test.app;

                import com.test.lib.LibClass;

                public class AppClass {
                  public void run() {
                    new LibClass().hello();
                  }
                }
                """
                  .trimIndent()
              )
              .withPath("com.test.app", "AppClass")
              .build()
          )
      }
      .write()
      .also { project -> convertIncludesToAllProjectsFile(project, dslKind) }

  /**
   * Generates maven-publish configuration script for a module. Uses the git commit SHA as version
   * and publishes to the configured local maven directory.
   */
  private fun mavenPublishConfig(artifactId: String): String =
    """
    apply plugin: 'maven-publish'

    java {
      withSourcesJar()
    }

    // Get version from git commit SHA
    def getGitCommitSha() {
      def stdout = new ByteArrayOutputStream()
      exec {
        commandLine 'git', 'rev-parse', 'HEAD'
        standardOutput = stdout
      }
      return stdout.toString().trim()
    }

    publishing {
      publications {
        maven(MavenPublication) {
          groupId = '$mavenGroup'
          artifactId = '$artifactId'
          version = getGitCommitSha()
          from components.java
        }
      }
      repositories {
        maven {
          name = 'artifactSwapLocal'
          url = uri('$mavenLocalPath')
        }
      }
    }
    """
      .trimIndent()

  /**
   * Creates gradle.properties configured for publishing, including the local maven repository path.
   */
  private fun gradlePropertiesForPublishing() =
    GradleProperties.of(
      "artifactswap.enabled=true",
      "artifactswap.primaryArtifactsMavenGroup=$mavenGroup",
      "artifactswap.artifactoryBaseUrl=https://example.com/artifactory/",
      "artifactswap.primaryRepositoryName=test-repo",
      "artifactswap.bomArtifactId=bom",
      "artifactswap.eventstreamBaseUrl=https://example.com/eventstream/",
      "artifactswap.artifactoryPublisherTokenFileName=test-token.txt",
      "artifactswap.bomSourceBranchName=main",
      "artifactswap.mavenLocalDirectory=${mavenLocalPath}",
      "org.gradle.caching=false",
      "org.gradle.configuration-cache=false",
    )

  /**
   * Publishes test artifacts to maven local for the given modules. Uses the git commit SHA as the
   * BOM version, matching how artifact-swap works in production.
   *
   * NOTE: This is the legacy method that creates fake artifacts. For E2E tests, use
   * [createPublishableProject] and [runGradlePublish] instead.
   */
  fun publishArtifactsToMavenLocal(project: GradleProject, modules: List<String>) {
    val commitSha = getGitCommitSha(project)

    // Publish each module with commit SHA as version
    modules.forEach { module ->
      val artifactId = module.removePrefix(":").replace(":", "_")
      val groupPath = mavenGroup.replace(".", "/")
      val artifactDir = mavenLocalPath.resolve(groupPath).resolve(artifactId).resolve(commitSha)

      artifactDir.toFile().mkdirs()

      // Create a minimal POM
      val pomFile = artifactDir.resolve("$artifactId-$commitSha.pom").toFile()
      pomFile.writeText(
        """<?xml version="1.0" encoding="UTF-8"?>
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>$mavenGroup</groupId>
  <artifactId>$artifactId</artifactId>
  <version>$commitSha</version>
  <name>$artifactId</name>
</project>"""
      )

      // Create a minimal JAR
      val jarFile = artifactDir.resolve("$artifactId-$commitSha.jar").toFile()
      jarFile.writeBytes(byteArrayOf()) // Empty jar

      // Create sources JAR (required for artifact validation)
      val sourcesJarFile = artifactDir.resolve("$artifactId-$commitSha-sources.jar").toFile()
      sourcesJarFile.writeBytes(byteArrayOf())
    }

    // Create BOM with commit SHA as version
    publishBomToMavenLocal(modules, commitSha)
  }

  private fun publishBomToMavenLocal(modules: List<String>, bomVersion: String) {
    val groupPath = mavenGroup.replace(".", "/")
    val bomDir = mavenLocalPath.resolve(groupPath).resolve("bom").resolve(bomVersion)

    bomDir.toFile().mkdirs()

    val dependencies =
      modules.joinToString("\n") { module ->
        val artifactId = module.removePrefix(":").replace(":", "_")
        """
        <dependency>
          <groupId>$mavenGroup</groupId>
          <artifactId>$artifactId</artifactId>
          <version>$bomVersion</version>
        </dependency>
      """
          .trimIndent()
      }

    val pomFile = bomDir.resolve("bom-$bomVersion.pom").toFile()
    pomFile.writeText(
      """<?xml version="1.0" encoding="UTF-8"?>
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>$mavenGroup</groupId>
  <artifactId>bom</artifactId>
  <version>$bomVersion</version>
  <name>bom</name>
  <packaging>pom</packaging>
  <dependencyManagement>
    <dependencies>
$dependencies
    </dependencies>
  </dependencyManagement>
</project>"""
    )
  }
}
