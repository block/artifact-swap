package xyz.block.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import xyz.block.artifactswap.artifactSyncBomService
import xyz.block.artifactswap.gradle.artifactSwapCoordinates
import xyz.block.artifactswap.gradle.internal.artifactSwapConfig
import xyz.block.gradle.services.services

/**
 * Returns a dependency that will resolve to either a project dependency or a maven artifact
 * dependency based on whether artifact swap is active.
 *
 * When artifact swap is active, this converts the project path to its corresponding maven
 * coordinates using the BOM for version resolution.
 *
 * @param path The project path (e.g., ":common:utils")
 * @return A Dependency that resolves to either the project or the maven artifact
 */
public fun Project.swappableProject(path: String): Dependency {
  return if (isArtifactSwapActive) {
    // Convert project path to artifact module name: ":common:utils" -> "common_utils"
    val artifactModule = path.artifactSwapCoordinates
    val mavenGroup = artifactSwapConfig.primaryArtifactsMavenGroup
    val version = gradle.services.artifactSyncBomService.bomVersionMap[artifactModule]
    dependencies.create("$mavenGroup:$artifactModule:$version")
  } else {
    dependencies.project(mapOf("path" to path))
  }
}
