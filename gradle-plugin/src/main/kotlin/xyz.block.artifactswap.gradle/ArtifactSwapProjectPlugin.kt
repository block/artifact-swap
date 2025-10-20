package xyz.block.artifactswap.gradle

import xyz.block.artifactswap.gradle.services.artifactSwapBomService
import xyz.block.artifactswap.gradle.services.artifactSwapConfigService
import xyz.block.artifactswap.gradle.services.services
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySubstitution
import org.gradle.api.artifacts.component.ModuleComponentSelector

/**
 * Artifact Swap project sub-plugin. This plugin is responsible for performing dependency substitution
 *
 * This plugin should not be applied directly. It is auto-applied by [ArtifactSwapSettingsPlugin].
 * For reference and searchability, the ID of this plugin is `xyz.block.artifactswap`.
 */
@Suppress("unused")
class ArtifactSwapProjectPlugin : Plugin<Project> {
  override fun apply(target: Project) = target.run {
    // Get configuration from the config service
    val configService = gradle.services.artifactSwapConfigService
    val bomService = gradle.services.artifactSwapBomService

    // Gather all necessary data for the substitution strategy
    val includedProjects = collectIncludedProjects()

    // Create the substitution strategy with pure logic (easily testable)
    val strategy = DependencySubstitutionStrategy(
      artifactSwapMavenGroup = configService.mavenGroup,
      bomVersionMap = bomService.bomVersionMap,
      includedProjects = includedProjects
    )

    // Apply substitutions using Gradle APIs
    substituteAllDependencies {
      val requested = requested as? ModuleComponentSelector ?: return@substituteAllDependencies

      // Given a project structure like this:
      //
      // :hobbits
      //   implementation project(':isengard')
      // :isengard
      //   implementation project(':mordor')
      // :mordor
      //
      // If a user requests to swap only `:hobits` and `:mordor`, `:isengard` is replaced by
      // ArtifactSwapSettingsPlugin with a maven reference to `com.squareup.register.sandbags:isengard`.
      //
      // The `com.squareup.sandbags:isengard` artifact will have a dependency on the
      // `com.squareup.sandbags:mordor` artifact. Since `:mordor` is included in the build, we
      // need to update the `com.squareup.sandbags:isengard` dependency to point to
      // `project(':mordor')`. Doing this update ensures that if the developer modifies `:mordor`,
      // those edits take effect everywhere that uses `:mordor` code, aka in regular gradle projects
      // and published artifacts. If we did not perform this substitution, the published artifact
      // would not respond to changes in `:mordor`.

      when (val decision = strategy.decide(requested.group, requested.module)) {
        is SubstitutionDecision.NoSubstitution -> {
          // Do nothing - keep the original dependency
        }
        is SubstitutionDecision.UseProject -> {
          // Substitute with the local project
          findProject(decision.projectPath)?.let { useTarget(it) }
        }
        is SubstitutionDecision.UseMavenArtifact -> {
          // Force Gradle to use the BOM specified version for this artifact.
          // Artifacts are only published when their source changes, so their POM references the
          // artifact versions of other projects at the time of publication. Referenced projects
          // may change and be re-published, so these versions contained in the artifact POM may
          // become out-of-date. The Artifact Swap BOM contains the most recent versions of each
          // artifact so we use that as our source of truth.
          useTarget(decision.coordinates)
        }
      }
      // TODO: In the future, we should rewrite all of the dependencies of artifacts to the
      //  current versions used by the build as specified by dependencies.gradle, or in the
      //  future, the version catalog. We anticipate external lib dependencies to change less
      // often than the set of modules a developer is targeting for current work, so we hold
      // off on that here and expect there to be few issues for the time being.
    }
  }

  /**
   * Collects all project paths that are included in the current build.
   */
  private fun Project.collectIncludedProjects(): Set<String> {
    return gradle.includedBuilds.flatMap { it.projectDir.listFiles()?.toList() ?: emptyList() }
      .mapNotNull { it.name.takeIf { name -> name.startsWith(":") } }
      .toSet() + rootProject.allprojects.map { it.path }.toSet()
  }
}

/**
 * The Gradle DSL for this is obnoxiously deeply nested, so just abstract it into a simple wrapper DSL
 */
private fun Project.substituteAllDependencies(substitution: DependencySubstitution.() -> Unit) {
  configurations.configureEach { configs ->
    if (configs.isCanBeResolved) {
      configs.resolutionStrategy { resolution ->
        resolution.dependencySubstitution { subs ->
          subs.all substitution@{ dep ->
            dep.substitution()
          }
        }
      }
    }
  }
}
