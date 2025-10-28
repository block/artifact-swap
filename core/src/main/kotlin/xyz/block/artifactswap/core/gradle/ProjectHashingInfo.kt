package xyz.block.artifactswap.core.gradle

import java.nio.file.Path

/**
 * Gradle project info used for hashing, a subset of what we fetch from Gradle's APIs.
 */
data class ProjectHashingInfo(
    val projectPath: String,
    val projectDirectory: Path,
    val filesToHash: Sequence<Path>,
)
