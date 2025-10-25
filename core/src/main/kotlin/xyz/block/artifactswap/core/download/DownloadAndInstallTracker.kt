package xyz.block.artifactswap.core.download

import xyz.block.artifactswap.core.download.models.ArtifactDownloaderResult
import xyz.block.artifactswap.core.download.models.DownloadedArtifactFileResult
import xyz.block.artifactswap.core.download.models.DownloadedArtifactFileResult.Failure
import xyz.block.artifactswap.core.download.models.DownloadedArtifactFileResult.Success
import xyz.block.artifactswap.core.download.models.InstallArtifactFilesResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

private const val UNKNOWN = -1L
// okhttp sets content length to -1 if it is unknown
private const val UNKNOWN_DOWNLOAD_SIZE = UNKNOWN

private const val BYTES_IN_MB = 1024 * 1024

/**
 * Monitors data about count/timing of downloads/installs of maven artifacts into a local
 * maven repo.
 *
 */
class DownloadAndInstallTracker {

  // This ensures that we don't have multiple coroutines updating the same data at the same time
  private val mutex = Mutex()

  private var countLocallyPresentArtifactFiles: Long = UNKNOWN
  private var countFilesToCheckInArtifactory: Long = UNKNOWN
  private var totalDurationMs: Long = UNKNOWN

  // we download artifacts concurrently/async, this records the time it takes to download each
  // artifact, not wall-time
  private val individualDownloadTimes = arrayListOf<Duration>()
  private var successfulDownloadsCount: Long = UNKNOWN
  private var failedDownloadsCount: Long = UNKNOWN
  private var totalDownloadResults: Long = UNKNOWN
  private var totalDownloadSizeBytes: Long = UNKNOWN

  // we also don't block installs on waiting for all downloads to complete, so we record the time
  // it takes to install each artifact, won't be wall-time for this process
  private val individualInstallTimes = arrayListOf<Duration>()
  private var successfulInstallsCount: Long = UNKNOWN
  private var failedInstallsCount: Long = UNKNOWN

  /**
   * Records the download results for all files associated with an artifact where we attempted
   * to download that file.
   *
   * An artifact can have multiple files associated with it that need downloading.
   * See [xyz.block.artifactswap.core.download.models.DownloadFileType] enum.
   */
  suspend fun recordFilesDownloadedForArtifactResult(results: List<DownloadedArtifactFileResult>) {
    mutex.withLock {
      if (successfulDownloadsCount == UNKNOWN_DOWNLOAD_SIZE) {
        successfulDownloadsCount = 0
      }
      successfulDownloadsCount += results.count { it is Success }
      if (failedDownloadsCount == UNKNOWN_DOWNLOAD_SIZE) {
        failedDownloadsCount = 0
      }
      failedDownloadsCount += results.count { it is Failure }
      if (totalDownloadResults == UNKNOWN_DOWNLOAD_SIZE) {
        totalDownloadResults = 0
      }
      totalDownloadResults += results.size
      if (totalDownloadSizeBytes == UNKNOWN_DOWNLOAD_SIZE) {
        totalDownloadSizeBytes = 0L
      }
      totalDownloadSizeBytes += results.filterIsInstance<Success>()
        .filter { it.fileContentsSizeBytes != UNKNOWN_DOWNLOAD_SIZE } // okhttp reports
        .sumOf { it.fileContentsSizeBytes }

      results.forEach { result ->
        when (result) {
          is Success -> individualDownloadTimes.add(result.downloadDurationMs)
          is Failure -> individualDownloadTimes.add(result.downloadDuration)
          is DownloadedArtifactFileResult.NoFileExists -> { /* no-op */
          }
        }
      }
    }
  }

  /**
   * Records the installation results for all files associated with an artifact where we attempted
   * to install that file after downloading it.
   *
   * An artifact can have multiple files associated with it that need downloading.
   * See [xyz.block.artifactswap.core.download.models.DownloadFileType] enum.
   */
  suspend fun recordArtifactFilesInstallationResults(installResults: InstallArtifactFilesResult) {
    mutex.withLock {
      if (successfulInstallsCount == UNKNOWN) {
        successfulInstallsCount = 0
      }
      if (failedInstallsCount == UNKNOWN) {
        failedInstallsCount = 0
      }
      when (installResults) {
        is InstallArtifactFilesResult.Success -> successfulInstallsCount++
        is InstallArtifactFilesResult.Failure -> failedInstallsCount++
        is InstallArtifactFilesResult.NoOp -> { /* no-op */ }
      }
      when (installResults) {
        is InstallArtifactFilesResult.Success -> individualInstallTimes.add(installResults.duration)
        is InstallArtifactFilesResult.Failure -> individualInstallTimes.add(installResults.duration)
        is InstallArtifactFilesResult.NoOp -> { /* no-op */ }
      }
    }
  }

  suspend fun updateLocalArtifactFileCount(additionalLocalFilesSeen: Int) {
    mutex.withLock {
      if (countLocallyPresentArtifactFiles == UNKNOWN) {
        countLocallyPresentArtifactFiles = 0
      }
      countLocallyPresentArtifactFiles += additionalLocalFilesSeen
    }
  }

  suspend fun updateArtifactFilesToDownloadCount(additionalFilesToDownload: Int) {
    mutex.withLock {
      if (countFilesToCheckInArtifactory == UNKNOWN) {
        countFilesToCheckInArtifactory = 0
      }
      countFilesToCheckInArtifactory += additionalFilesToDownload
    }
  }

  suspend fun recordWallClockDuration(wallClockDuration: Duration) {
    mutex.withLock {
      totalDurationMs = wallClockDuration.inWholeMilliseconds
    }
  }

  suspend fun getDownloadAndInstallData(): DownloadAndInstallData {
    mutex.withLock {
      val downloadTimesMs = individualDownloadTimes.sorted()
      val downloadPercentiles = getRelevantPercentiles(downloadTimesMs)

      val installTimesMs = individualInstallTimes.sorted()
      val installPercentiles = getRelevantPercentiles(installTimesMs)

      val finalResult = if (failedDownloadsCount > successfulDownloadsCount * 0.1) {
          ArtifactDownloaderResult.MANY_DOWNLOADS_FAILED
      } else if (failedInstallsCount > successfulInstallsCount * 0.1) {
          ArtifactDownloaderResult.MANY_INSTALLS_FAILED
      } else {
          ArtifactDownloaderResult.SUCCESS
      }

      return DownloadAndInstallData(
        result = finalResult,
        countLocallyPresentArtifactFiles = countLocallyPresentArtifactFiles,
        countFilesToCheckInArtifactory = countFilesToCheckInArtifactory,
        countSuccessfulDownloadedArtifactFiles = successfulDownloadsCount,
        countFailedDownloadedArtifactFiles = failedDownloadsCount,
        countSuccessfulInstalledArtifacts = successfulInstallsCount,
        countFailedInstalledArtifacts = failedInstallsCount,
        totalDurationMs = totalDurationMs,
        totalDownloadSizeMb = if (totalDownloadSizeBytes == UNKNOWN_DOWNLOAD_SIZE) {
          -1.0
        } else {
          totalDownloadSizeBytes.toDouble() / BYTES_IN_MB
        },
        p50DownloadTime = downloadPercentiles.p50,
        p90DownloadTime = downloadPercentiles.p90,
        p99DownloadTime = downloadPercentiles.p99,
        maxDownloadTime = downloadPercentiles.max,
        p50InstallTime = installPercentiles.p50,
        p90InstallTime = installPercentiles.p90,
        p99InstallTime = installPercentiles.p99,
        maxInstallTime = installPercentiles.max,
      )
    }
  }

  private data class DurationPercentiles(
    val p50: Duration,
    val p90: Duration,
    val p99: Duration,
    val max: Duration,
  )

  private fun getRelevantPercentiles(durations: List<Duration>): DurationPercentiles {
    val p50Duration = if (durations.isNotEmpty()) {
      durations[durations.size / 2]
    } else {
      Duration.INFINITE
    }
    val p90DownloadTimeMs = if (durations.isNotEmpty()) {
      durations[(durations.size * 0.9).toInt()]
    } else {
      Duration.INFINITE
    }
    val p99DownloadTimeMs = if (durations.isNotEmpty()) {
      durations[(durations.size * 0.99).toInt()]
    } else {
      Duration.INFINITE
    }
    val maxDuration = if (durations.isNotEmpty()) durations.last() else Duration.INFINITE
    return DurationPercentiles(
      p50 = p50Duration,
      p90 = p90DownloadTimeMs,
      p99 = p99DownloadTimeMs,
      max = maxDuration,
    )
  }
}

data class DownloadAndInstallData(
    val result: ArtifactDownloaderResult = ArtifactDownloaderResult.NOT_SET,
    val countLocallyPresentArtifactFiles: Long = -1,
    val countFilesToCheckInArtifactory: Long = -1,
    val countSuccessfulDownloadedArtifactFiles: Long = -1,
    val countFailedDownloadedArtifactFiles: Long = -1,
    val countSuccessfulInstalledArtifacts: Long = -1,
    val countFailedInstalledArtifacts: Long = -1,
    val totalDurationMs: Long = -1,
    val totalDownloadSizeMb: Double = -1.0,
    val getArtifactsToDownloadDurationMs: Long = -1,
    val p50DownloadTime: Duration = Duration.INFINITE,
    val p90DownloadTime: Duration = Duration.INFINITE,
    val p99DownloadTime: Duration = Duration.INFINITE,
    val maxDownloadTime: Duration = Duration.INFINITE,
    val p50InstallTime: Duration = Duration.INFINITE,
    val p90InstallTime: Duration = Duration.INFINITE,
    val p99InstallTime: Duration = Duration.INFINITE,
    val maxInstallTime: Duration = Duration.INFINITE,
)
