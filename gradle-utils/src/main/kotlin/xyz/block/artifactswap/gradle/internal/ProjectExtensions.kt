package xyz.block.artifactswap.gradle.internal

import org.gradle.api.Project

/** Checks if this is an Android project. */
public val Project.isAndroidLibrary: Boolean
  get() = pluginManager.hasPlugin("com.android.base")

/** Checks if this is a Kotlin project. */
public val Project.isKotlin: Boolean
  get() =
    pluginManager.hasPlugin("org.jetbrains.kotlin.android") ||
      pluginManager.hasPlugin("org.jetbrains.kotlin.jvm")

public val Project.hasPublishableComponent: Boolean
  get() =
    pluginManager.hasPlugin("com.android.library") ||
      pluginManager.hasPlugin("org.jetbrains.kotlin.jvm") ||
      pluginManager.hasPlugin("java") ||
      pluginManager.hasPlugin("java-library")

@Suppress("GradleProjectIsolation") // It's actually ok
public val Project.isRootProject: Boolean
  get() = this == this.rootProject
