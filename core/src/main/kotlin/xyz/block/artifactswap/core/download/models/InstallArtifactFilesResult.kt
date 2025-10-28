package xyz.block.artifactswap.core.download.models

import kotlin.time.Duration

/**
 * Represents the result of installing artifacts.
 *
 * If we are asked to install 0 artifacts, we return NoOp,
 * otherwise we return Success or Failure based on whether
 * any of the file I/O operations fails.
 */
sealed interface InstallArtifactFilesResult {
  data object NoOp : InstallArtifactFilesResult
  data class Success(
    val duration: Duration,
  ) : InstallArtifactFilesResult
  data class Failure(
    val duration: Duration
  ) : InstallArtifactFilesResult
}
