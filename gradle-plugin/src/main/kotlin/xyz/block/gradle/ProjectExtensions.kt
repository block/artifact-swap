package xyz.block.gradle

import org.gradle.api.Project

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
