package xyz.block.artifactswap.functionaltest

import com.autonomousapps.kit.GradleProject
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Path
import kotlin.io.path.readLines
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xyz.block.artifactswap.functionaltest.fixtures.ArtifactSwapTestProject
import xyz.block.artifactswap.functionaltest.fixtures.CliRunner
import xyz.block.artifactswap.functionaltest.fixtures.build
import xyz.block.artifactswap.functionaltest.fixtures.ideSync

/**
 * End-to-end functional tests that exercise the full CLI workflow.
 *
 * Unlike other functional tests that create fake artifacts directly, these tests use the actual CLI
 * tooling for hashing and BOM publishing, and the real Gradle publish plugin for artifact
 * publishing. This ensures the entire workflow is tested as it would be used in production.
 *
 * The workflow tested:
 * 1. Create a multi-module Gradle project with publish plugin
 * 2. Initialize git repository
 * 3. Run CLI `hashing` command to generate project hashes
 * 4. Run Gradle to publish artifacts to local maven using publish plugin
 * 5. Run CLI `bom-publisher --local` to publish BOM to local maven
 * 6. Verify artifacts and BOM exist in local maven repository
 * 7. Run IDE sync and verify artifact swap works with published artifacts
 */
class EndToEndCliTest {

  @TempDir lateinit var mavenLocalPath: Path

  private lateinit var testProject: ArtifactSwapTestProject

  @BeforeEach
  fun setup() {
    testProject = ArtifactSwapTestProject(mavenLocalPath)
  }

  @Test
  fun `GIVEN project with CLI workflow WHEN hashing and publishing THEN lib swapped to artifact`() {
    // Given: Create a publishable project with the publish plugin applied
    val project = testProject.createPublishableProject(GradleProject.DslKind.GROOVY)

    // Initialize git repository (required for BOM versioning)
    testProject.initializeGitRepo(project)

    // Get the commit SHA which will be used as the BOM version
    val bomVersion = testProject.getGitCommitSha(project)

    // Create CLI runner
    val cliRunner = CliRunner.forProject(project, mavenLocalPath)

    // Configure IDE to only include :app - this allows :lib to be swapped out
    testProject.setupIdeProjectsList(project, listOf(":app"))

    // When: Run the full CLI workflow

    // Step 1: Run hashing command FIRST to generate content hashes
    // The BOM publisher expects artifacts to be versioned by their content hash
    val hashingOutputFile = project.rootDir.toPath().resolve(".gradle/sandbagHashes/hashes.txt")
    val hashingResult = cliRunner.runHashing(hashingOutputFile)

    // Then: Hashing should succeed
    if (!hashingResult.isSuccess) {
      throw AssertionError(
        "Hashing failed with exit code ${hashingResult.exitCode}:\n${hashingResult.output}"
      )
    }
    assertThat(hashingOutputFile.toFile().exists()).isTrue()

    // Parse hash file to get content hashes for each project
    val hashLines = hashingOutputFile.readLines()
    assertThat(hashLines).isNotEmpty()
    assertThat(hashLines.any { it.startsWith(":lib|") }).isTrue()
    assertThat(hashLines.any { it.startsWith(":app|") }).isTrue()

    val projectHashes =
      hashLines.associate { line ->
        val parts = line.split("|")
        parts[0] to parts[1] // :projectPath -> contentHash
      }

    val libHash = projectHashes[":lib"]!!
    val appHash = projectHashes[":app"]!!

    // Step 2: Publish artifacts using the content hashes as versions
    // This mimics how production works - artifacts are versioned by content hash
    testProject.publishArtifactWithVersion(project, ":lib", libHash)
    testProject.publishArtifactWithVersion(project, ":app", appHash)

    // Verify artifacts were published to local maven with content hashes
    assertThat(cliRunner.verifyArtifactPublished(testProject.mavenGroup, "lib", libHash)).isTrue()
    assertThat(cliRunner.verifyArtifactPublished(testProject.mavenGroup, "app", appHash)).isTrue()

    // Step 3: Run BOM publisher with --local flag
    val bomResult = cliRunner.runBomPublisher(bomVersion, hashingOutputFile)

    // Then: BOM publishing should succeed
    if (!bomResult.isSuccess) {
      throw AssertionError(
        "BOM publishing failed with exit code ${bomResult.exitCode}:\n${bomResult.output}"
      )
    }
    assertThat(cliRunner.verifyBomPublished(testProject.mavenGroup, bomVersion)).isTrue()

    // Step 4: Run IDE sync to verify artifact swap uses the published artifacts
    val syncResult = project.ideSync(":app:dependencies", "--configuration", "runtimeClasspath")

    // Then: IDE sync should succeed and show artifact swap is active
    assertThat(syncResult.task(":help")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(syncResult.output).contains("Using Artifact Swap!")
    assertThat(syncResult.output).contains("Artifact Swap module selection")

    // Verify :lib was excluded (swapped to artifact) - only :app should be selected
    assertThat(syncResult.output).contains("1 selected out of 2 candidates")
    assertThat(syncResult.output).contains("excluded: 1")

    // The dependency tree should show the maven artifact for lib (swapped)
    assertThat(syncResult.output).contains("${testProject.mavenGroup}:lib")
  }

  @Test
  fun `GIVEN project with local changes WHEN running CLI workflow THEN changed modules included`() {
    // Given: Create a publishable project
    val project = testProject.createPublishableProject(GradleProject.DslKind.GROOVY)
    testProject.initializeGitRepo(project)
    val bomVersion = testProject.getGitCommitSha(project)
    val cliRunner = CliRunner.forProject(project, mavenLocalPath)

    // Only include :app in IDE — :lib would normally be swapped
    testProject.setupIdeProjectsList(project, listOf(":app"))

    // Run initial workflow to publish artifacts
    val hashingOutputFile = project.rootDir.toPath().resolve(".gradle/sandbagHashes/hashes.txt")
    val hashResult = cliRunner.runHashing(hashingOutputFile)
    assertThat(hashResult.isSuccess).isTrue()

    // Parse hash file to get content hashes and publish with those versions
    val projectHashes =
      hashingOutputFile.readLines().associate { line ->
        val parts = line.split("|")
        parts[0] to parts[1]
      }
    testProject.publishArtifactWithVersion(project, ":lib", projectHashes[":lib"]!!)
    testProject.publishArtifactWithVersion(project, ":app", projectHashes[":app"]!!)

    val bomResult = cliRunner.runBomPublisher(bomVersion, hashingOutputFile)
    assertThat(bomResult.isSuccess).isTrue()

    // Add a new file to lib module (after publishing)
    val newFile = File(project.rootDir, "lib/src/main/java/com/test/lib/NewFile.java")
    newFile.parentFile.mkdirs()
    newFile.writeText(
      """
      package com.test.lib;
      public class NewFile {}
      """
        .trimIndent()
    )

    // When: Run IDE sync — :lib has local changes so it should NOT be swapped
    val syncResult = project.ideSync()

    // Then: Both modules selected: :app (explicit) and :lib (local changes prevent swap)
    assertThat(syncResult.task(":help")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(syncResult.output).contains("Using Artifact Swap!")
    assertThat(syncResult.output).contains("Artifact Swap module selection")
    assertThat(syncResult.output).contains("local changes:")
    assertThat(syncResult.output).contains("2 selected out of 2 candidates")
    assertThat(syncResult.output).contains("excluded: 0")
  }

  @Test
  fun `GIVEN CLI hashing output WHEN parsing hash file THEN correct format`() {
    // Given: Create a project and run hashing
    val project = testProject.createPublishableProject(GradleProject.DslKind.GROOVY)
    testProject.initializeGitRepo(project)
    val cliRunner = CliRunner.forProject(project, mavenLocalPath)

    // When: Run hashing
    val hashingOutputFile = project.rootDir.toPath().resolve(".gradle/sandbagHashes/hashes.txt")
    val result = cliRunner.runHashing(hashingOutputFile)

    // Then: Hash file should have correct format: <project-path>|<hash>
    if (!result.isSuccess) {
      throw AssertionError("Hashing failed (exit code ${result.exitCode}):\n${result.output}")
    }

    // Verify the hash file exists
    assertThat(hashingOutputFile.toFile().exists()).isTrue()

    val hashLines = hashingOutputFile.readLines()

    hashLines.forEach { line ->
      assertThat(line).contains("|")
      val parts = line.split("|")
      assertThat(parts).hasSize(2)
      assertThat(parts[0]).startsWith(":")
      assertThat(parts[1]).isNotEmpty()
    }
  }

  @Test
  fun `GIVEN published artifacts with no local changes WHEN IDE sync THEN modules swapped to artifacts`() {
    // Given: Create a publishable project
    val project = testProject.createPublishableProject(GradleProject.DslKind.GROOVY)
    testProject.initializeGitRepo(project)
    val bomVersion = testProject.getGitCommitSha(project)
    val cliRunner = CliRunner.forProject(project, mavenLocalPath)

    // Configure IDE to only include :app - this allows :lib to be swapped out
    testProject.setupIdeProjectsList(project, listOf(":app"))

    // Run hashing to get content hashes
    val hashingOutputFile = project.rootDir.toPath().resolve(".gradle/sandbagHashes/hashes.txt")
    val hashResult = cliRunner.runHashing(hashingOutputFile)
    assertThat(hashResult.isSuccess).isTrue()

    // Parse hashes and publish artifacts with those versions
    val projectHashes =
      hashingOutputFile.readLines().associate { line ->
        val parts = line.split("|")
        parts[0] to parts[1]
      }
    testProject.publishArtifactWithVersion(project, ":lib", projectHashes[":lib"]!!)
    testProject.publishArtifactWithVersion(project, ":app", projectHashes[":app"]!!)

    // Publish BOM
    val bomResult = cliRunner.runBomPublisher(bomVersion, hashingOutputFile)
    assertThat(bomResult.isSuccess).isTrue()

    // When: Run IDE sync WITHOUT any local changes
    // With :lib having a published artifact and not in ide-projects.txt, it should be excluded
    val syncResult = project.ideSync(":app:dependencies", "--configuration", "runtimeClasspath")

    // Then: IDE sync should succeed with artifact swap active
    assertThat(syncResult.task(":help")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(syncResult.output).contains("Using Artifact Swap!")
    assertThat(syncResult.output).contains("Artifact Swap module selection")

    // Verify that :lib was excluded (swapped to artifact) because:
    // - It's not in ide-projects.txt
    // - It has a published artifact with matching content hash
    // - It has no local changes
    // The selection should show "excluded: 1" for :lib being swapped
    assertThat(syncResult.output).contains("excluded: 1")

    // The dependency tree should show the maven artifact for lib (not project :lib)
    assertThat(syncResult.output).contains("${testProject.mavenGroup}:lib")
  }

  @Test
  fun `GIVEN missing artifact WHEN IDE sync THEN module included due to missing artifact`() {
    // Given: Create a publishable project
    val project = testProject.createPublishableProject(GradleProject.DslKind.GROOVY)
    testProject.initializeGitRepo(project)
    val bomVersion = testProject.getGitCommitSha(project)
    val cliRunner = CliRunner.forProject(project, mavenLocalPath)

    // Configure IDE to only include :app - :lib would be swapped IF it had an artifact
    testProject.setupIdeProjectsList(project, listOf(":app"))

    // Run hashing to get content hashes
    val hashingOutputFile = project.rootDir.toPath().resolve(".gradle/sandbagHashes/hashes.txt")
    val hashResult = cliRunner.runHashing(hashingOutputFile)
    assertThat(hashResult.isSuccess).isTrue()

    // Parse hashes
    val projectHashes =
      hashingOutputFile.readLines().associate { line ->
        val parts = line.split("|")
        parts[0] to parts[1]
      }

    // Only publish :app artifact - deliberately DON'T publish :lib
    testProject.publishArtifactWithVersion(project, ":app", projectHashes[":app"]!!)

    // Publish BOM (it will only include :app since :lib isn't published)
    val bomResult = cliRunner.runBomPublisher(bomVersion, hashingOutputFile)
    // BOM publisher might fail or succeed with partial artifacts - that's okay for this test

    // When: Run IDE sync - :lib should be included because its artifact is missing
    val syncResult = project.ideSync(":app:dependencies", "--configuration", "runtimeClasspath")

    // Then: IDE sync should succeed with artifact swap active
    assertThat(syncResult.task(":help")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(syncResult.output).contains("Using Artifact Swap!")
    assertThat(syncResult.output).contains("Artifact Swap module selection")

    // Verify :lib was included due to missing artifact (not excluded/swapped)
    // Both modules should be selected: :app (explicit) and :lib (missing artifact)
    assertThat(syncResult.output).contains("2 selected out of 2 candidates")
    assertThat(syncResult.output).contains("missing artifact: 1")
    assertThat(syncResult.output).contains("excluded: 0")

    // The dependency tree should show :lib as a project dependency (not artifact)
    assertThat(syncResult.output).contains("project :lib")
  }

  @Test
  fun `GIVEN Gradle publish WHEN running publish task THEN artifacts in correct location`() {
    // Given: Create a publishable project
    val project = testProject.createPublishableProject(GradleProject.DslKind.GROOVY)
    testProject.initializeGitRepo(project)
    val bomVersion = testProject.getGitCommitSha(project)

    // When: Run Gradle publish for lib module
    val publishResult = project.build(":lib:publishMavenPublicationToArtifactSwapLocalRepository")

    // Then: Artifact should be published to correct location
    assertThat(
        publishResult.task(":lib:publishMavenPublicationToArtifactSwapLocalRepository")?.outcome
      )
      .isEqualTo(TaskOutcome.SUCCESS)

    // Verify POM file exists at expected location
    val groupPath = testProject.mavenGroup.replace(".", "/")
    val expectedPomPath =
      mavenLocalPath
        .resolve(groupPath)
        .resolve("lib")
        .resolve(bomVersion)
        .resolve("lib-$bomVersion.pom")

    assertThat(expectedPomPath.toFile().exists()).isTrue()

    // Verify JAR file exists
    val expectedJarPath =
      mavenLocalPath
        .resolve(groupPath)
        .resolve("lib")
        .resolve(bomVersion)
        .resolve("lib-$bomVersion.jar")

    assertThat(expectedJarPath.toFile().exists()).isTrue()
  }
}
