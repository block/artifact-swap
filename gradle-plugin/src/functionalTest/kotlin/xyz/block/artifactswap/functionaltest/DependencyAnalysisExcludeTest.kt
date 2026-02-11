package xyz.block.artifactswap.functionaltest

import com.autonomousapps.kit.GradleProject
import com.google.common.truth.Truth.assertThat
import java.nio.file.Path
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xyz.block.artifactswap.core.module_selector.InclusionReason.EXCLUDED
import xyz.block.artifactswap.core.module_selector.InclusionReason.EXPLICITLY_REQUESTED
import xyz.block.artifactswap.functionaltest.fixtures.ArtifactSwapTestProject
import xyz.block.artifactswap.functionaltest.fixtures.artifactSwapSelection
import xyz.block.artifactswap.functionaltest.fixtures.ideSync

/**
 * Tests that dependency-analysis plugin's exclude() method works correctly with artifact swap.
 *
 * This verifies the fix for the issue where the dependency-analysis plugin's
 * `exclude(projects.foo)` DSL was receiving artifact notation strings instead of real project
 * accessors, causing "Could not find method exclude()" errors.
 */
class DependencyAnalysisExcludeTest {

  @TempDir lateinit var mavenRepo: Path

  private lateinit var testProject: ArtifactSwapTestProject

  @BeforeEach
  fun setup() {
    testProject = ArtifactSwapTestProject(mavenRepo)
  }

  @Test
  fun `GIVEN dependency-analysis with exclude WHEN syncing with artifact swap active THEN succeeds`() {
    // Setup: Create a project with dependency-analysis configuration
    val project = testProject.createDagpProject(GradleProject.DslKind.GROOVY)

    // Initialize git repository (required for BOM version determination)
    testProject.initializeGitRepo(project)

    // Publish artifacts so swap is possible
    testProject.publishArtifactsToMavenLocal(project, listOf(":common-ui", ":app"))

    // Only include :app in IDE — :common-ui would normally be swapped, but since
    // :app depends on it and DAGP's exclude() references it, we need to verify
    // the build still succeeds when artifact swap rewrites project() references
    testProject.setupIdeProjectsList(project, listOf(":app"))

    // When: Run IDE sync
    val result = project.ideSync()

    // Then: Build should succeed with artifact swap active
    assertThat(result.task(":help")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.output).contains("Using Artifact Swap!")

    val selection = result.artifactSwapSelection()
    assertThat(selection.explicitCount).isEqualTo(1)
    assertThat(selection.excludedCount).isEqualTo(1)
    assertThat(selection.decisionFor(":app")).isEqualTo(EXPLICITLY_REQUESTED)
    assertThat(selection.decisionFor(":common-ui")).isEqualTo(EXCLUDED)

    // Verify no errors about missing exclude() method — this confirms
    // DAGP's exclude(':common-ui') still works when artifact swap is active
    assertThat(result.output).doesNotContain("Could not find method exclude()")
    assertThat(result.output).doesNotContain("autonomousapps.extension.Issue")
  }
}
