package xyz.block.artifactswap.functionaltest

import com.autonomousapps.kit.GradleProject
import com.google.common.truth.Truth.assertThat
import java.nio.file.Path
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xyz.block.artifactswap.core.module_selector.InclusionReason.ALWAYS_KEEP
import xyz.block.artifactswap.core.module_selector.InclusionReason.EXCLUDED
import xyz.block.artifactswap.core.module_selector.InclusionReason.EXPLICITLY_REQUESTED
import xyz.block.artifactswap.core.module_selector.InclusionReason.LOCAL_CHANGES
import xyz.block.artifactswap.core.module_selector.InclusionReason.MISSING_ARTIFACT
import xyz.block.artifactswap.functionaltest.fixtures.ArtifactSwapTestProject
import xyz.block.artifactswap.functionaltest.fixtures.artifactSwapSelection
import xyz.block.artifactswap.functionaltest.fixtures.ideSync
import xyz.block.artifactswap.functionaltest.fixtures.writeFile

/**
 * Basic functional tests for artifact swap plugin.
 *
 * Tests the core functionality of swapping project dependencies with published artifacts during IDE
 * sync, including various selection scenarios like local changes, missing artifacts, etc.
 */
class BasicArtifactSwapTest {

  @TempDir lateinit var mavenRepo: Path

  private lateinit var testProject: ArtifactSwapTestProject

  @BeforeEach
  fun setup() {
    testProject = ArtifactSwapTestProject(mavenRepo)
  }

  @Test
  fun `GIVEN all modules with artifacts WHEN IDE sync with subset THEN excluded modules swapped`() {
    val project = testProject.createBasicProject(GradleProject.DslKind.GROOVY)

    // Initialize git and publish all modules
    testProject.initializeGitRepo(project)
    testProject.publishArtifactsToMavenLocal(project, listOf(":lib", ":app"))

    // Only include :app in IDE — :lib should be swapped to an artifact
    testProject.setupIdeProjectsList(project, listOf(":app"))

    // When: Run IDE sync with dependencies task to see dependency tree
    val result = project.ideSync(":app:dependencies", "--configuration", "runtimeClasspath")

    // Then: Should use artifact swap and swap :lib to artifact
    assertThat(result.task(":help")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.output).contains("Using Artifact Swap!")

    val selection = result.artifactSwapSelection()
    assertThat(selection.totalSelected).isEqualTo(1)
    assertThat(selection.totalCandidates).isEqualTo(2)
    assertThat(selection.explicitCount).isEqualTo(1)
    assertThat(selection.excludedCount).isEqualTo(1)
    assertThat(selection.decisionFor(":app")).isEqualTo(EXPLICITLY_REQUESTED)
    assertThat(selection.decisionFor(":lib")).isEqualTo(EXCLUDED)

    // Dependency tree should show lib as a maven artifact, not a project dependency
    assertThat(result.output).contains("${testProject.mavenGroup}:lib")
  }

  @Test
  fun `GIVEN module with local changes WHEN IDE sync THEN keeps as project dependency`() {
    val project = testProject.createBasicProject(GradleProject.DslKind.GROOVY)

    // Initialize git and publish
    testProject.initializeGitRepo(project)
    testProject.publishArtifactsToMavenLocal(project, listOf(":lib", ":app"))

    // Only include :app in IDE — :lib would normally be swapped
    testProject.setupIdeProjectsList(project, listOf(":app"))

    // Add a new file to lib module — this should prevent it from being swapped
    project.writeFile(
      "lib/src/main/java/com/test/lib/NewFile.java",
      """
      package com.test.lib;
      public class NewFile {}
      """
        .trimIndent(),
    )

    // When: Run IDE sync
    val result = project.ideSync()

    // Then: lib should be included (not swapped) due to local changes
    assertThat(result.task(":help")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.output).contains("Using Artifact Swap!")

    val selection = result.artifactSwapSelection()
    assertThat(selection.totalSelected).isEqualTo(2)
    assertThat(selection.totalCandidates).isEqualTo(2)
    assertThat(selection.explicitCount).isEqualTo(1)
    assertThat(selection.localChangesCount).isEqualTo(1)
    assertThat(selection.excludedCount).isEqualTo(0)
    assertThat(selection.decisionFor(":app")).isEqualTo(EXPLICITLY_REQUESTED)
    assertThat(selection.decisionFor(":lib")).isEqualTo(LOCAL_CHANGES)
  }

  @Test
  fun `GIVEN module missing artifact WHEN IDE sync THEN includes as project`() {
    val project = testProject.createBasicProject(GradleProject.DslKind.GROOVY)

    // Initialize git and publish only :app (not :lib)
    testProject.initializeGitRepo(project)
    testProject.publishArtifactsToMavenLocal(project, listOf(":app")) // lib not published

    // Only include :app in IDE — :lib would be swapped if it had an artifact
    testProject.setupIdeProjectsList(project, listOf(":app"))

    // When: Run IDE sync
    val result = project.ideSync()

    // Then: Both modules selected: :app (explicit) and :lib (missing artifact prevents swap)
    assertThat(result.task(":help")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.output).contains("Using Artifact Swap!")

    val selection = result.artifactSwapSelection()
    assertThat(selection.totalSelected).isEqualTo(2)
    assertThat(selection.totalCandidates).isEqualTo(2)
    assertThat(selection.explicitCount).isEqualTo(1)
    assertThat(selection.missingArtifactCount).isEqualTo(1)
    assertThat(selection.excludedCount).isEqualTo(0)
    assertThat(selection.decisionFor(":app")).isEqualTo(EXPLICITLY_REQUESTED)
    assertThat(selection.decisionFor(":lib")).isEqualTo(MISSING_ARTIFACT)
  }

  @Test
  fun `GIVEN module in always-keep list WHEN IDE sync THEN never swapped`() {
    val project = testProject.createBasicProject(GradleProject.DslKind.GROOVY)

    // Initialize git and publish
    testProject.initializeGitRepo(project)
    testProject.publishArtifactsToMavenLocal(project, listOf(":lib", ":app"))

    // Only include :app in IDE — :lib would normally be swapped
    testProject.setupIdeProjectsList(project, listOf(":app"))

    // Mark lib as always-keep — this should override the swap and keep it as a project
    testProject.setupAlwaysKeepList(project, listOf(":lib"))

    // When: Run IDE sync
    val result = project.ideSync()

    // Then: Both selected: :app (explicit) and :lib (always-keep prevents swap)
    assertThat(result.task(":help")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.output).contains("Using Artifact Swap!")

    val selection = result.artifactSwapSelection()
    assertThat(selection.totalSelected).isEqualTo(2)
    assertThat(selection.totalCandidates).isEqualTo(2)
    assertThat(selection.explicitCount).isEqualTo(1)
    assertThat(selection.alwaysKeepCount).isEqualTo(1)
    assertThat(selection.excludedCount).isEqualTo(0)
    assertThat(selection.decisionFor(":app")).isEqualTo(EXPLICITLY_REQUESTED)
    assertThat(selection.decisionFor(":lib")).isEqualTo(ALWAYS_KEEP)
  }

  @Test
  fun `GIVEN project dependency WHEN swapped to artifact THEN dependency tree shows artifact`() {
    val project = testProject.createBasicProject(GradleProject.DslKind.GROOVY)

    // Initialize git and publish
    testProject.initializeGitRepo(project)
    testProject.publishArtifactsToMavenLocal(project, listOf(":lib", ":app"))

    // Only include :app in IDE — :lib should be swapped to an artifact
    testProject.setupIdeProjectsList(project, listOf(":app"))

    // When: Run dependencies task to see the dependency tree
    val result = project.ideSync(":app:dependencies", "--configuration", "compileClasspath")

    // Then: Should show lib as artifact dependency, not project dependency
    assertThat(result.task(":help")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":app:dependencies")).isNotNull()

    val selection = result.artifactSwapSelection()
    assertThat(selection.explicitCount).isEqualTo(1)
    assertThat(selection.excludedCount).isEqualTo(1)
    assertThat(selection.decisionFor(":app")).isEqualTo(EXPLICITLY_REQUESTED)
    assertThat(selection.decisionFor(":lib")).isEqualTo(EXCLUDED)

    // The dependency tree should show the artifact notation for swapped projects
    // This verifies that project(':lib') was actually swapped to artifact
    assertThat(result.output).contains("${testProject.mavenGroup}:lib")
    assertThat(result.output).doesNotContain("project :lib")
  }
}
