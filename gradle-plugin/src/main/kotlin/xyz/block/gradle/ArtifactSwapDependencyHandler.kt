@file:Suppress("UnstableApiUsage")

package xyz.block.gradle

import javax.inject.Inject
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import xyz.block.artifactswap.artifactSyncBomService
import xyz.block.gradle.services.services

/**
 * A dependency handler that can swap out external module dependencies for local project
 * dependencies, when artifact-swap ([artifactSwapIsActive]) is enabled. This is entirely
 * transparent to users.
 *
 * @see [org.gradle.kotlin.dsl.project]
 */
public abstract class ArtifactSwapDependencyHandler
@Inject
constructor(private val project: Project, private val artifactSwapIsActive: Boolean) {
  internal companion object {
    const val NAME = "artifactSwapDependencies"

    fun create(project: Project, artifactSwapIsActive: Boolean) {
      project.dependencies.extensions.create(
        NAME,
        ArtifactSwapDependencyHandler::class.java,
        project,
        artifactSwapIsActive,
      )
    }
  }

  /**
   * Conditionally swaps in an external module dependency for a local project dependency.
   *
   * nb: this must return a [ModuleDependency] (even though we could return a
   * [org.gradle.api.artifacts.Dependency] without recourse to `as` casts), because the Kotlin DSL
   * expects a `ModuleDependency` for use with configuration actions against declared dependencies.
   * For example, with:
   * ```
   * implementation(project(":foo:bar")) { exclude("bar:baz:1.0") }
   * ```
   *
   * the `exclude()` function is actually an extension function on `ModuleDependency` (not
   * `Dependency`). Relatedly,
   * ```
   * implementation(project(":foo:bar")) { isTransitive = false }
   * ```
   *
   * just uses the Java method `ModuleDependency setTransitive(boolean transitive)`.
   */
  internal fun project(path: String): ModuleDependency {
    val dependencies = project.dependencies

    // If artifact-swap is not enabled, use the base Gradle API. We can't just "not call this
    // function" in that case because this function will ALWAYS be on the classpath due to how it's
    // implemented.
    if (!artifactSwapIsActive) {
      return dependencies.project(mapOf("path" to path, "configuration" to null))
        as ProjectDependency
    }

    val artifact = project.gradle.services.artifactSyncBomService.bomVersionMap[path]
    if (artifact == null) {
      return dependencies.project(mapOf("path" to path)) as ProjectDependency
    } else {
      project.logger.info(
        "Replacing project dependency for $path in ${project.path} with artifact $artifact"
      )
      return project.dependencyFactory.create(artifact)
    }
  }
}
