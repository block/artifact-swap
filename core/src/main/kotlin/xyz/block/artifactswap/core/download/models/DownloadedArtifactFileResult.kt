package xyz.block.artifactswap.core.download.models

import okhttp3.ResponseBody
import kotlin.time.Duration

/**
 * Represents the result of downloading an artifact from Artifactory.
 *
 * Main states are:
 * - we download an artifact successfully (200)
 * - we request something that doesn't exist (400)
 * - something goes wrong during the request (500)
 *
 */
sealed interface DownloadedArtifactFileResult {
  data class Success(
      val artifact: Artifact,
      val downloadFileType: DownloadFileType,
      val fileContents: ResponseBody,
      val fileContentsSizeBytes: Long,
      val downloadDurationMs: Duration
  ) : DownloadedArtifactFileResult

  data class NoFileExists(
      val artifact: Artifact,
      val downloadFileType: DownloadFileType
  ) : DownloadedArtifactFileResult

  data class Failure(
      val artifact: Artifact,
      val downloadFileType: DownloadFileType,
      val throwable: Throwable,
      val downloadDuration: Duration
  ) : DownloadedArtifactFileResult
}
