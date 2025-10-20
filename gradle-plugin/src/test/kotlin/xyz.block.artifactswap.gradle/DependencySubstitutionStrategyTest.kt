package xyz.block.artifactswap.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DependencySubstitutionStrategyTest {

  private val testMavenGroup = "com.example.test"
  private val testBomVersionMap = mapOf(
    "module-a" to "1.0.0",
    "module-b" to "2.0.0",
    "nested_module" to "3.0.0"
  )

  @Test
  fun `should not substitute when group doesn't match`() {
    val strategy = DependencySubstitutionStrategy(
      artifactSwapMavenGroup = testMavenGroup,
      bomVersionMap = testBomVersionMap,
      includedProjects = setOf(":module-a")
    )

    val decision = strategy.decide(
      group = "different.group",
      module = "module-a"
    )

    assertIs<SubstitutionDecision.NoSubstitution>(decision)
  }

  @Test
  fun `should not substitute when group is null`() {
    val strategy = DependencySubstitutionStrategy(
      artifactSwapMavenGroup = testMavenGroup,
      bomVersionMap = testBomVersionMap,
      includedProjects = setOf(":module-a")
    )

    val decision = strategy.decide(
      group = null,
      module = "module-a"
    )

    assertIs<SubstitutionDecision.NoSubstitution>(decision)
  }

  @Test
  fun `should not substitute when module is null`() {
    val strategy = DependencySubstitutionStrategy(
      artifactSwapMavenGroup = testMavenGroup,
      bomVersionMap = testBomVersionMap,
      includedProjects = setOf(":module-a")
    )

    val decision = strategy.decide(
      group = testMavenGroup,
      module = null
    )

    assertIs<SubstitutionDecision.NoSubstitution>(decision)
  }

  @Test
  fun `should substitute with project when project is included`() {
    val strategy = DependencySubstitutionStrategy(
      artifactSwapMavenGroup = testMavenGroup,
      bomVersionMap = testBomVersionMap,
      includedProjects = setOf(":module-a", ":module-b")
    )

    val decision = strategy.decide(
      group = testMavenGroup,
      module = "module-a"
    )

    assertIs<SubstitutionDecision.UseProject>(decision)
    assertEquals(":module-a", decision.projectPath)
  }

  @Test
  fun `should substitute with maven artifact when project is not included`() {
    val strategy = DependencySubstitutionStrategy(
      artifactSwapMavenGroup = testMavenGroup,
      bomVersionMap = testBomVersionMap,
      includedProjects = setOf(":other-module")
    )

    val decision = strategy.decide(
      group = testMavenGroup,
      module = "module-a"
    )

    assertIs<SubstitutionDecision.UseMavenArtifact>(decision)
    assertEquals("com.example.test:module-a:1.0.0", decision.coordinates)
  }

  @Test
  fun `should handle nested module names with underscores`() {
    val strategy = DependencySubstitutionStrategy(
      artifactSwapMavenGroup = testMavenGroup,
      bomVersionMap = testBomVersionMap,
      includedProjects = setOf(":nested:module")
    )

    val decision = strategy.decide(
      group = testMavenGroup,
      module = "nested_module"
    )

    assertIs<SubstitutionDecision.UseProject>(decision)
    assertEquals(":nested:module", decision.projectPath)
  }

  @Test
  fun `should use maven artifact with BOM version for deeply nested module`() {
    val strategy = DependencySubstitutionStrategy(
      artifactSwapMavenGroup = testMavenGroup,
      bomVersionMap = mapOf("foo_bar_baz" to "4.5.6"),
      includedProjects = emptySet()
    )

    val decision = strategy.decide(
      group = testMavenGroup,
      module = "foo_bar_baz"
    )

    assertIs<SubstitutionDecision.UseMavenArtifact>(decision)
    assertEquals("com.example.test:foo_bar_baz:4.5.6", decision.coordinates)
  }

  @Test
  fun `should not substitute when module not in BOM and not included`() {
    val strategy = DependencySubstitutionStrategy(
      artifactSwapMavenGroup = testMavenGroup,
      bomVersionMap = testBomVersionMap,
      includedProjects = emptySet()
    )

    val decision = strategy.decide(
      group = testMavenGroup,
      module = "unknown-module"
    )

    assertIs<SubstitutionDecision.NoSubstitution>(decision)
  }

  @Test
  fun `should prefer project over maven even when both are available`() {
    val strategy = DependencySubstitutionStrategy(
      artifactSwapMavenGroup = testMavenGroup,
      bomVersionMap = testBomVersionMap,
      includedProjects = setOf(":module-a")
    )

    val decision = strategy.decide(
      group = testMavenGroup,
      module = "module-a"
    )

    // Should use the local project, not the maven artifact
    assertIs<SubstitutionDecision.UseProject>(decision)
    assertEquals(":module-a", decision.projectPath)
  }

  @Test
  fun `should handle empty BOM map`() {
    val strategy = DependencySubstitutionStrategy(
      artifactSwapMavenGroup = testMavenGroup,
      bomVersionMap = emptyMap(),
      includedProjects = setOf(":module-a")
    )

    val decision = strategy.decide(
      group = testMavenGroup,
      module = "module-a"
    )

    // Should still substitute with project
    assertIs<SubstitutionDecision.UseProject>(decision)
  }

  @Test
  fun `should handle empty included projects`() {
    val strategy = DependencySubstitutionStrategy(
      artifactSwapMavenGroup = testMavenGroup,
      bomVersionMap = testBomVersionMap,
      includedProjects = emptySet()
    )

    val decision = strategy.decide(
      group = testMavenGroup,
      module = "module-a"
    )

    // Should use maven artifact since no projects are included
    assertIs<SubstitutionDecision.UseMavenArtifact>(decision)
  }

  @Test
  fun `should correctly convert module names to project paths`() {
    val strategy = DependencySubstitutionStrategy(
      artifactSwapMavenGroup = testMavenGroup,
      bomVersionMap = emptyMap(),
      includedProjects = setOf(":a:b:c:d")
    )

    val decision = strategy.decide(
      group = testMavenGroup,
      module = "a_b_c_d"
    )

    assertIs<SubstitutionDecision.UseProject>(decision)
    assertEquals(":a:b:c:d", decision.projectPath)
  }
}
