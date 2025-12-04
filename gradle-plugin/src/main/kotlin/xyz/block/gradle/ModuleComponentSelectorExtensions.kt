package xyz.block.gradle

import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.jetbrains.kotlin.util.prefixIfNot
import xyz.block.artifactswap.ArtifactSwapSettingsPlugin

/**
 * This reverses the project -> maven dependency notation rewriting performed by
 * [ArtifactSwapSettingsPlugin].
 */
internal val ModuleComponentSelector.asProjectPath: String
  get() = module.replace("_", ":").prefixIfNot(":")
