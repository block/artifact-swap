package xyz.block.artifactswap.idea.util

import com.intellij.openapi.project.Project
import xyz.block.artifactswap.idea.gradle.ArtifactSwapService
import xyz.block.artifactswap.model.ArtifactSwapModel

/** Gets the artifact swap model from the most recent Gradle sync, or null if unavailable. */
val Project.artifactSwapModel: ArtifactSwapModel?
  get() = ArtifactSwapService.getInstance(this).model
