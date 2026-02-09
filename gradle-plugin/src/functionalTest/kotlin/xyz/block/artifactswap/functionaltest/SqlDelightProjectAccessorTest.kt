package xyz.block.artifactswap.functionaltest

import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.truth.BuildTaskSubject.Companion.assertThat
import com.google.common.truth.Truth.assertThat
import java.nio.file.Path
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xyz.block.artifactswap.functionaltest.fixtures.ArtifactSwapTestProject
import xyz.block.artifactswap.functionaltest.fixtures.ideSync

/**
 * Tests that SQLDelight project dependencies work correctly with artifact swap.
 *
 * This test verifies the fix for the issue where SQLDelight's `dependency projects.foo` DSL was
 * receiving artifact notation strings instead of real project accessors, causing "Could not find
 * method dependency()" errors.
 */
class SqlDelightProjectAccessorTest {

  @TempDir lateinit var mavenRepo: Path

  private lateinit var testProject: ArtifactSwapTestProject

  @BeforeEach
  fun setup() {
    testProject = ArtifactSwapTestProject(mavenRepo)
  }

  @Test
  fun `GIVEN SQLDelight project with always-keep dependency WHEN syncing THEN succeeds`() {
    // Setup: Create a project with SQLDelight configuration
    val project = testProject.createSqlDelightProject(GradleProject.DslKind.GROOVY)

    // Initialize git repository (required for BOM version determination)
    testProject.initializeGitRepo(project)

    // Only include :consumer in IDE — :db would normally be swapped to an artifact,
    // but SQLDelight's `dependency projects.db` needs the real project accessor
    testProject.setupIdeProjectsList(project, listOf(":consumer"))

    // Mark :db as always-keep so it remains a real project despite not being in ide-projects.txt
    testProject.setupAlwaysKeepList(project, listOf(":db"))

    // Publish test artifacts to maven local (simulating pre-published artifacts)
    testProject.publishArtifactsToMavenLocal(project, listOf(":db", ":consumer"))

    // When: Run IDE sync
    val result = project.ideSync()

    // Then: Build should succeed with both modules included
    assertThat(result.task(":help")).isNotNull()
    assertThat(result.task(":help")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.output).contains("Using Artifact Swap!")

    // :db kept via always-keep, :consumer via explicit request — nothing swapped
    assertThat(result.output).contains("2 selected out of 2 candidates")
    assertThat(result.output).contains("excluded: 0")

    // Verify no errors about missing dependency() method — this confirms
    // SQLDelight's `dependency projects.db` received a real project accessor
    assertThat(result.output).doesNotContain("Could not find method dependency()")
    assertThat(result.output).doesNotContain("SqlDelightDatabase")
  }
}
