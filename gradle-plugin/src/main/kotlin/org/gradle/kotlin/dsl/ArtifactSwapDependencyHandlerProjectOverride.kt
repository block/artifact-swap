package org.gradle.kotlin.dsl

import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import xyz.block.gradle.ArtifactSwapDependencyHandler

private val DependencyHandler.artifactSwapDependencies: ArtifactSwapDependencyHandler
  get() = extensions.getByType(ArtifactSwapDependencyHandler::class.java)

/**
 * Creates a dependency on the project with path [path]. If artifact-swap is enabled, will swap in a
 * published external module instead of resolving to the local project.
 *
 * This function essentially overrides the `DependencyHandler#project()` function provided by the
 * base Gradle API.
 *
 * @see [xyz.block.gradle.ArtifactSwapDependencyHandler]
 */
public fun DependencyHandler.project(path: String): ModuleDependency {
  return artifactSwapDependencies.project(path)
}
