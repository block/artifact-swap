package xyz.block.artifactswap.core.download

import kotlin.time.measureTime
import kotlin.time.measureTimedValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.apache.logging.log4j.kotlin.logger
import xyz.block.artifactswap.core.config.ArtifactSwapConfig
import xyz.block.artifactswap.core.download.models.Artifact
import xyz.block.artifactswap.core.download.models.ArtifactDownloaderEvent
import xyz.block.artifactswap.core.download.models.ArtifactDownloaderResult
import xyz.block.artifactswap.core.download.models.DownloadFileType
import xyz.block.artifactswap.core.download.models.LocalArtifactState.NOT_INSTALLED
import xyz.block.artifactswap.core.download.services.ArtifactDownloaderEventStream
import xyz.block.artifactswap.core.download.services.ArtifactRepository
import xyz.block.artifactswap.core.download.services.ArtifactSyncBomLoader
import xyz.block.artifactswap.core.utils.inWholeMillisecondsIfFinite

class ArtifactDownloader(
  val bomLoader: ArtifactSyncBomLoader,
  val artifactEventStream: ArtifactDownloaderEventStream,
  val artifactRepository: ArtifactRepository,
  val config: ArtifactSwapConfig,
) {

  /**
   * Downloads and installs Maven artifacts based on a BOM file.
   *
   * @param bomVersion The BOM version to use. If blank, will find the best BOM version.
   * @return ArtifactDownloaderEvent containing analytics data about the download process.
   */
  suspend fun downloadAndInstallArtifacts(bomVersion: String = ""): ArtifactDownloaderEvent =
    coroutineScope {
      var analyticsEvent = ArtifactDownloaderEvent()

      // Determine BOM version to use
      val resolvedBomVersion =
        bomVersion.ifBlank {
          bomLoader.findBestBomVersion(checkRemote = true).getOrElse {
            analyticsEvent =
              analyticsEvent.copy(
                result = ArtifactDownloaderResult.FAILED_TO_FIND_VALID_BOM_VERSION
              )
            logEvent(analyticsEvent)
            return@coroutineScope analyticsEvent
          }
        }

      logger.warn("Using BOM version: $resolvedBomVersion")

      // Get artifacts listed in BOM
      val (artifactsListedInBom, bomArtifactsDiscoveryDuration) =
        measureTimedValue {
          artifactRepository.getArtifactsInBom(resolvedBomVersion).getOrElse {
            analyticsEvent =
              analyticsEvent.copy(result = ArtifactDownloaderResult.FAILED_TO_DOWNLOAD_BOM)
            logEvent(analyticsEvent)
            return@coroutineScope analyticsEvent
          }
        }

      analyticsEvent =
        analyticsEvent.copy(
          countArtifactsToDownload = artifactsListedInBom.size,
          getArtifactsToDownloadDurationMs =
            bomArtifactsDiscoveryDuration.inWholeMillisecondsIfFinite,
        )

      // Download and install all artifacts
      val downloadAndInstallTracker = DownloadAndInstallTracker()
      val downloadAndInstallWallDuration = measureTime {
        coroutineScope {
          artifactsListedInBom.forEach { artifact ->
            launch {
              val fileTypesNeedingDownload = getFileTypesNeedingDownload(artifact)
              val locallyPresentFileTypes =
                DownloadFileType.entries.filter { it !in fileTypesNeedingDownload }
              downloadAndInstallTracker.updateLocalArtifactFileCount(locallyPresentFileTypes.size)
              downloadAndInstallTracker.updateArtifactFilesToDownloadCount(
                fileTypesNeedingDownload.size
              )

              // Spin up each download asynchronously
              val fileDownloadResults =
                fileTypesNeedingDownload
                  .map { fileType ->
                    async { artifactRepository.downloadArtifactFile(artifact, fileType) }
                  }
                  .awaitAll()

              // Await all the individual download results and pass them to the install tracker
              downloadAndInstallTracker.recordFilesDownloadedForArtifactResult(fileDownloadResults)
              val installResults =
                artifactRepository.installDownloadedArtifactFiles(fileDownloadResults)
              downloadAndInstallTracker.recordArtifactFilesInstallationResults(installResults)
            }
          }
        }
      }
      downloadAndInstallTracker.recordWallClockDuration(downloadAndInstallWallDuration)
      analyticsEvent =
        updateEventWithDownloadAndInstallData(downloadAndInstallTracker, analyticsEvent)
      logEvent(analyticsEvent)

      return@coroutineScope analyticsEvent
    }

  private suspend fun updateEventWithDownloadAndInstallData(
    downloadAndInstallTracker: DownloadAndInstallTracker,
    analyticsEvent: ArtifactDownloaderEvent,
  ): ArtifactDownloaderEvent {
    val downloadAndInstallAnalyticsData = downloadAndInstallTracker.getDownloadAndInstallData()
    return analyticsEvent.copy(
      result = downloadAndInstallAnalyticsData.result,
      totalDurationMs = downloadAndInstallAnalyticsData.totalDurationMs,
      totalDownloadSizeMb = downloadAndInstallAnalyticsData.totalDownloadSizeMb.toLong(),
      p50DownloadTimeMs =
        downloadAndInstallAnalyticsData.p50DownloadTime.inWholeMillisecondsIfFinite,
      p90DownloadTimeMs =
        downloadAndInstallAnalyticsData.p90DownloadTime.inWholeMillisecondsIfFinite,
      p99DownloadTimeMs =
        downloadAndInstallAnalyticsData.p99DownloadTime.inWholeMillisecondsIfFinite,
      maxDownloadTimeMs =
        downloadAndInstallAnalyticsData.maxDownloadTime.inWholeMillisecondsIfFinite,
      p50InstallTimeMs = downloadAndInstallAnalyticsData.p50InstallTime.inWholeMillisecondsIfFinite,
      p90InstallTimeMs = downloadAndInstallAnalyticsData.p90InstallTime.inWholeMillisecondsIfFinite,
      p99InstallTimeMs = downloadAndInstallAnalyticsData.p99InstallTime.inWholeMillisecondsIfFinite,
      maxInstallTimeMs = downloadAndInstallAnalyticsData.maxInstallTime.inWholeMillisecondsIfFinite,
      countLocallyPresentArtifactFiles =
        downloadAndInstallAnalyticsData.countLocallyPresentArtifactFiles,
      countFilesToCheckInArtifactory =
        downloadAndInstallAnalyticsData.countFilesToCheckInArtifactory,
      countSuccessfulDownloadedArtifactFiles =
        downloadAndInstallAnalyticsData.countSuccessfulDownloadedArtifactFiles,
      countFailedDownloadedArtifactFiles =
        downloadAndInstallAnalyticsData.countFailedDownloadedArtifactFiles,
      countSuccessfulInstalledArtifacts =
        downloadAndInstallAnalyticsData.countSuccessfulInstalledArtifacts,
      countFailedInstalledArtifacts = downloadAndInstallAnalyticsData.countFailedInstalledArtifacts,
    )
  }

  private fun getFileTypesNeedingDownload(artifact: Artifact): List<DownloadFileType> {
    return DownloadFileType.entries.filter { fileType ->
      artifactRepository.getLocalArtifactState(artifact, fileType) == NOT_INSTALLED
    }
  }

  private suspend fun logEvent(analyticsEvent: ArtifactDownloaderEvent) {
    runCatching {
        logger.debug { "Sending event to eventstream: $analyticsEvent" }
        val result = artifactEventStream.sendEvents(listOf(analyticsEvent))
        if (result) {
          logger.debug { "Successfully sent event to eventstream" }
        } else {
          logger.debug { "Failed to send event to eventstream" }
        }
      }
      .getOrElse { error ->
        logger.debug { "Failed to send event to eventstream" }
        logger.debug(error) { "Eventstream error" }
      }
  }
}
