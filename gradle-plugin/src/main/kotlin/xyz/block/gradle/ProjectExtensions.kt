package xyz.block.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import xyz.block.artifactswap.artifactSwapConfig
import xyz.block.artifactswap.artifactSyncBomService
import xyz.block.gradle.services.services

/** Checks if this is an Android project. */
val Project.isAndroidLibrary: Boolean
  get() = pluginManager.hasPlugin("com.android.base")

/** Checks if this is a Kotlin project. */
val Project.isKotlin: Boolean
  get() =
    pluginManager.hasPlugin("org.jetbrains.kotlin.android") ||
      pluginManager.hasPlugin("org.jetbrains.kotlin.jvm")

val Project.hasPublishableComponent: Boolean
  get() =
    pluginManager.hasPlugin("com.android.library") ||
      pluginManager.hasPlugin("org.jetbrains.kotlin.jvm") ||
      pluginManager.hasPlugin("java") ||
      pluginManager.hasPlugin("java-library")

/**
 * Converts a project path to a sandbag artifact name. Example: ":hobbits:frodo" -> "hobbits_frodo"
 */
val Project.artifactSwapCoordinates: String
  get() = path.removePrefix(":").replace(":", "_")

internal val String.artifactSwapCoordinates: String
  get() = this.removePrefix(":").replace(":", "_")

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
