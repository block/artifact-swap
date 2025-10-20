package xyz.block.artifactswap.gradle

/**
 * Core logic for deciding how to substitute dependencies when artifacts and
 * projects are mixed together in a build.
 *
 * Note, this class is independent of Gradle APIs to allow for easy unit testing.
 */
class DependencySubstitutionStrategy(
  private val artifactSwapMavenGroup: String,
  private val bomVersionMap: Map<String, String>,
  private val includedProjects: Set<String>
) {
  /**
   * Determines how a dependency should be substituted based on its group and module.
   *
   * @param group The dependency group (e.g., "com.example.artifactswap.artifacts")
   * @param module The dependency module/artifact name (e.g. "my_gradle_module_path")
   * @return A [SubstitutionDecision] indicating what action to take
   */
  fun decide(group: String?, module: String?): SubstitutionDecision {
    // Only substitute if the group matches our artifact swap group
    if (group != artifactSwapMavenGroup || module == null) {
      return SubstitutionDecision.NoSubstitution
    }

    // Convert module name to project path (e.g., "foo_bar" -> ":foo:bar")
    val projectPath = module.toProjectPath()

    // If the project is included in the build, substitute with the project
    if (projectPath in includedProjects) {
      return SubstitutionDecision.UseProject(projectPath)
    }

    // Otherwise, use the BOM version for the maven artifact
    val version = bomVersionMap[module]
    return if (version != null) {
      SubstitutionDecision.UseMavenArtifact("$group:$module:$version")
    } else {
      // If no version in BOM, don't substitute
      SubstitutionDecision.NoSubstitution
    }
  }

  /**
   * Converts a module name to a project path.
   * Example: "foo_bar" -> ":foo:bar"
   */
  private fun String.toProjectPath(): String =
    replace("_", ":").prefixIfNot(":")

  private fun String.prefixIfNot(prefix: String): String =
    if (startsWith(prefix)) this else "$prefix$this"
}

/**
 * Represents the decision for how to substitute a dependency.
 */
sealed interface SubstitutionDecision {
  /** No substitution should be performed */
  object NoSubstitution : SubstitutionDecision

  /** Substitute with a local project */
  data class UseProject(val projectPath: String) : SubstitutionDecision

  /** Substitute with a maven artifact at a specific version */
  data class UseMavenArtifact(val coordinates: String) : SubstitutionDecision
}
