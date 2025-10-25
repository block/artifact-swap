package xyz.block.artifactswap.core.remover

import xyz.block.artifactswap.core.remover.models.ArtifactRemoverEventResult
import xyz.block.artifactswap.core.remover.models.ArtifactRemoverResult
import xyz.block.artifactswap.core.remover.models.DeleteOldArtifactsResult
import xyz.block.artifactswap.core.remover.models.DeleteOldBomsResult
import xyz.block.artifactswap.core.remover.services.ArtifactRemoverEventStream
import xyz.block.artifactswap.core.remover.services.InstalledBom
import xyz.block.artifactswap.core.remover.services.InstalledProject
import xyz.block.artifactswap.core.remover.services.LocalArtifactRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import org.apache.logging.log4j.kotlin.logger
import java.io.IOException
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class ArtifactRemover(
  val artifactEventStream: ArtifactRemoverEventStream,
  val artifactRepository: LocalArtifactRepository
) {
  companion object {
    // conservative estimate, a new bom comes in when a user pulls green-master
    private const val EXPECTED_BOMS_PER_DAY = 2

    // keep two weeks of boms by default
    const val NUMBER_OF_BOMS_TO_KEEP = EXPECTED_BOMS_PER_DAY * 14
  }

  suspend fun removeArtifacts(numberOfBomsToKeep: Int = NUMBER_OF_BOMS_TO_KEEP): ArtifactRemoverResult = coroutineScope {
    logger.debug { "Starting artifact remover" }
    var result = ArtifactRemoverResult()
    val overallDuration = measureTime {
      try {
        // we take this opportunity to do some measurements of m2 for monitoring. This takes some time ~10s, if
        // the command hasn't been run in a while and m2 is large. But the command is expected to execute in the
        // background, so we accept that tradeoff.
        logger.debug { "Starting initial repository measurement" }
        artifactRepository.measureRepository()?.let { repoStats ->
          logger.debug { "Finished initial repository measurement: $repoStats" }
          result = result.copy(
            startRepoStats = repoStats
          )
        }
        val deferredDeleteOldArtifactsResult = async {
          logger.debug { "Starting delete old artifacts" }
          measureTimedValue {
            deleteOldArtifacts(numberOfBomsToKeep)
          }
        }
        val deferredDeleteOldBomsResult = async {
          logger.debug { "Starting delete old boms" }
          measureTimedValue {
            deleteOldBoms(numberOfBomsToKeep)
          }
        }
        deferredDeleteOldArtifactsResult.await().let { deleteOldArtifactsResultAndDuration ->
          logger.debug { "Finished deleting old artifacts" }
          logger.debug { "Attempted deletes: ${deleteOldArtifactsResultAndDuration.value.attemptedToDelete.size}" }
          logger.debug { "Successful deletes: ${deleteOldArtifactsResultAndDuration.value.successfulDeletion.size}" }
          logger.debug { "Failed deletes: ${deleteOldArtifactsResultAndDuration.value.failedDeletion.size}" }
          logger.debug { "Deleting old artifacts took: ${deleteOldArtifactsResultAndDuration.duration}" }
          result = result.copy(
            deleteOldArtifactsResult = deleteOldArtifactsResultAndDuration.value,
            deleteOldArtifactsDuration = deleteOldArtifactsResultAndDuration.duration,
          )
        }
        deferredDeleteOldBomsResult.await().let { deleteOldBomsResultAndDuration ->
          logger.debug { "Finished deleting old boms" }
          logger.debug { "Attempted deletes: ${deleteOldBomsResultAndDuration.value.attemptedDeletionBoms.size}" }
          logger.debug { "Successful deletes: ${deleteOldBomsResultAndDuration.value.successfulDeletionBoms.size}" }
          logger.debug { "Failed deletes: ${deleteOldBomsResultAndDuration.value.failedDeletionBoms.size}" }
          logger.debug { "Deleting old boms took: ${deleteOldBomsResultAndDuration.duration}" }
          result = result.copy(
            deleteOldBomsResult = deleteOldBomsResultAndDuration.value,
            deleteOldBomsDuration = deleteOldBomsResultAndDuration.duration,
          )
        }
        logger.debug { "Starting end repository measurement" }
        artifactRepository.measureRepository()?.let { repoStats ->
          logger.debug { "Finished end repository measurement: $repoStats" }
          result = result.copy(
            endRepoStats = repoStats
          )
        }
        result = result.copy(
          result = ArtifactRemoverEventResult.SUCCESS,
        )
      } catch (e: IOException) {
        logger.debug(e) { "Error while removing artifacts" }
        result = result.copy(
          result = ArtifactRemoverEventResult.FAILURE,
        )
      }
    }
    result = result.copy(
      totalDuration = overallDuration
    )
    result
  }

  private suspend fun deleteOldBoms(keepMostRecentCount: Int): DeleteOldBomsResult =
    coroutineScope {
      var deleteOldBomsResult = DeleteOldBomsResult()
      artifactRepository.getInstalledBomsByRecency(count = Int.MAX_VALUE)
        .drop(keepMostRecentCount)
        .map {
          async {
            it to artifactRepository.deleteInstalledBom(it)
          }
        }.awaitAll()
        .groupBy(
          keySelector = { (_, deletionSucceeded) -> deletionSucceeded },
          valueTransform = { (installedBom, _) -> installedBom }
        ).onEach { (deletionSucceeded, boms) ->
          deleteOldBomsResult = deleteOldBomsResult.copy(
            attemptedDeletionBoms = deleteOldBomsResult.attemptedDeletionBoms + boms
          )
          deleteOldBomsResult = if (deletionSucceeded) {
            deleteOldBomsResult.copy(
              successfulDeletionBoms = deleteOldBomsResult.successfulDeletionBoms + boms
            )
          } else {
            deleteOldBomsResult.copy(
              failedDeletionBoms = deleteOldBomsResult.failedDeletionBoms + boms
            )
          }
        }
      deleteOldBomsResult
    }

  private suspend fun deleteOldArtifacts(numberOfBomsToKeep: Int): DeleteOldArtifactsResult = coroutineScope {
    var result = DeleteOldArtifactsResult()
    val artifactsToKeep = async {
      val recentBoms = artifactRepository.getInstalledBomsByRecency(count = numberOfBomsToKeep)
      getArtifactsToKeep(recentBoms)
    }
    artifactRepository.getAllInstalledProjects()
      .mapNotNull { installedArtifact ->
        val versionsNotNeeded = installedArtifact.versions.filter { version ->
          artifactsToKeep.await()[installedArtifact.projectPath]?.let { versionsToKeep ->
            version !in versionsToKeep
          } ?: true // if this entire project is not known to any recent boms, delete it
        }.toSet()
        if (versionsNotNeeded.isNotEmpty()) {
          InstalledProject(
            installedArtifact.projectPath,
            installedArtifact.repositoryPath,
            versionsNotNeeded
          )
        } else {
          null
        }
      }.map {
        it to artifactRepository.deleteInstalledProjectVersions(it)
      }.collect { (totalArtifactDeleteInfo, versionsSuccessfullyDeleted) ->
        val anySuccessfulDeletes = versionsSuccessfullyDeleted.isNotEmpty()
        val anyFailedDeletes =
          (totalArtifactDeleteInfo.versions - versionsSuccessfullyDeleted).isNotEmpty()
        result = result.copy(
          attemptedToDelete = result.attemptedToDelete + totalArtifactDeleteInfo,
          successfulDeletion = if (anySuccessfulDeletes) result.successfulDeletion +
            totalArtifactDeleteInfo.onlyVersions(versionsSuccessfullyDeleted.toSet())
          else result.successfulDeletion,
          failedDeletion = if (anyFailedDeletes) result.failedDeletion +
            totalArtifactDeleteInfo.onlyVersions(
              totalArtifactDeleteInfo.versions - versionsSuccessfullyDeleted.toSet()
            )
          else result.failedDeletion
        )
      }
    result
  }

  /**
   * Given a collection of BOMs, returns a map from artifact ids (aka gradle project) to versions
   * of those artifacts that are present in the BOMs.
   */
  private fun getArtifactsToKeep(recentBoms: List<InstalledBom>): Map<String, Set<String>> {
    return recentBoms.flatMap { it.getArtifactsAndVersions().entries }
      .groupBy(
        keySelector = { artifactIdAndVersionId -> artifactIdAndVersionId.key },
        valueTransform = { artifactIdAndVersionId -> artifactIdAndVersionId.value })
      .mapValues { it.value.toSet() }
  }

  suspend fun logResult(analyticsEvent: ArtifactRemoverResult) {
    runCatching {
      logger.debug { "Sending event to eventstream: ${analyticsEvent.toArtifactRemoverEvent()}" }
      val result = artifactEventStream.sendResults(listOf(analyticsEvent))
      if (result) {
        logger.debug { "Successfully sent event to eventstream" }
      } else {
        logger.debug { "Failed to send event to eventstream" }
      }
    }.getOrElse { error ->
      logger.debug { "Failed to send event to eventstream" }
      logger.debug(error) { "Eventstream error" }
    }
  }
}
