package xyz.block.artifactswap.gradle

import org.gradle.api.Project

/**
 * Converts a project path to an artifact swap coordinate name. Example: ":hobbits:frodo" ->
 * "hobbits_frodo"
 */
public val Project.artifactSwapCoordinates: String
  get() = path.removePrefix(":").replace(":", "_")

/**
 * Converts a project path string to an artifact swap coordinate name. Example: ":hobbits:frodo" ->
 * "hobbits_frodo"
 */
public val String.artifactSwapCoordinates: String
  get() = this.removePrefix(":").replace(":", "_")
