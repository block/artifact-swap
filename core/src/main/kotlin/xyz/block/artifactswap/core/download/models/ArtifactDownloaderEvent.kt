package xyz.block.artifactswap.core.download.models

import xyz.block.artifactswap.core.eventstream.EventstreamEvent
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ArtifactDownloaderEvent(
  @Json(name = "result")
  val result: ArtifactDownloaderResult = ArtifactDownloaderResult.NOT_SET,
  @Json(name = "count_artifacts_to_download")
  val countArtifactsToDownload: Int = -1,
  // Note, we attempt to download up to 5 files per artifact, see DownloadFileType.kt
  // so this is expected to be higher than countArtifactsToDownload
  @Json(name = "count_artifacts_successfully_downloaded")
  val countSuccessfulDownloadedArtifactFiles: Long = -1L,
  @Json(name = "count_artifacts_failed_to_download")
  val countFailedDownloadedArtifactFiles: Long = -1L,
  @Json(name = "count_artifacts_successfully_installed")
  val countSuccessfulInstalledArtifacts: Long = -1L,
  @Json(name = "count_artifacts_failed_to_install")
  val countFailedInstalledArtifacts: Long = -1L,
  @Json(name = "total_duration_ms")
  val totalDurationMs: Long = -1L,
  @Json(name = "total_download_size_mb")
  val totalDownloadSizeMb: Long = -1L,
  @Json(name = "get_artifacts_to_download_duration_ms")
  val getArtifactsToDownloadDurationMs: Long = -1L,
  @Json(name = "download_artifacts_p50_duration_ms")
  val p50DownloadTimeMs: Long = -1L,
  @Json(name = "download_artifacts_p90_duration_ms")
  val p90DownloadTimeMs: Long = -1L,
  @Json(name = "download_artifacts_p99_duration_ms")
  val p99DownloadTimeMs: Long = -1L,
  @Json(name = "download_artifacts_max_duration_ms")
  val maxDownloadTimeMs: Long = -1L,
  @Json(name = "install_artifacts_p50_duration_ms")
  val p50InstallTimeMs: Long = -1L,
  @Json(name = "install_artifacts_p90_duration_ms")
  val p90InstallTimeMs: Long = -1L,
  @Json(name = "install_artifacts_p99_duration_ms")
  val p99InstallTimeMs: Long = -1L,
  @Json(name = "install_artifacts_max_duration_ms")
  val maxInstallTimeMs: Long = -1L,
  @Json(name = "count_locally_present_artifact_files")
  val countLocallyPresentArtifactFiles: Long = -1L,
  @Json(name = "count_files_to_check_in_artifactory")
  val countFilesToCheckInArtifactory: Long = -1L,
  @Json(name = "base_git_branch")
  val gitBranch: String = "",
  @Json(name = "base_git_sha")
  val gitSha: String = "",
  @Json(name = "ci_env")
  val ciEnv: String = "",
  @Json(name = "base_build_id")
  val buildId: String = "",
  @Json(name = "base_build_step_id")
  val buildStepId: String = "",
  @Json(name = "base_build_job_id")
  val buildJobId: String = "",
  @Json(name = "base_ci_type")
  val ciType: String = "",
  @Json(name = "user_ldap")
  val userLdap: String = System.getProperty("user.name"),
) {

  fun toEventStreamEvent(): EventstreamEvent {
    return EventstreamEvent(
      catalogName = "artifact_sync_artifact_downloader",
      appName = "artifact_sync",
      event = this
    )
  }
}
