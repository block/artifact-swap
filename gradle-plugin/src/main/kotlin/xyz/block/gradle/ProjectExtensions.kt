package xyz.block.gradle

import org.gradle.api.Project

/**
 * Checks if this is an Android project.
 */
val Project.isAndroid: Boolean
  get() = pluginManager.hasPlugin("com.android.base")

/**
 * Checks if this is a Kotlin project.
 */
val Project.isKotlin: Boolean
  get() = pluginManager.hasPlugin("org.jetbrains.kotlin.android") ||
    pluginManager.hasPlugin("org.jetbrains.kotlin.jvm")

/**
 * Converts a project path to a sandbag artifact name.
 * Example: ":hobbits:frodo" -> "hobbits_frodo"
 */
val String.toSandbagArtifact: String
  get() = removePrefix(":").replace(":", "_")
