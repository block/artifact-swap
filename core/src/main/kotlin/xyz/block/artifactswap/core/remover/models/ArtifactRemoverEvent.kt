package xyz.block.artifactswap.core.remover.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import xyz.block.artifactswap.core.eventstream.EventstreamEvent
import xyz.block.artifactswap.core.remover.services.InstalledBom
import xyz.block.artifactswap.core.remover.services.InstalledProject
import xyz.block.artifactswap.core.remover.services.RepositoryStats
import xyz.block.artifactswap.core.utils.inWholeMillisecondsIfFinite
import kotlin.time.Duration

enum class ArtifactRemoverEventResult {
  SUCCESS,
  FAILURE,
  UNKNOWN
}

data class ArtifactRemoverResult(
  @Json(name = "result")
  val result: ArtifactRemoverEventResult = ArtifactRemoverEventResult.UNKNOWN,
  @Json(ignore = true)
  val startRepoStats: RepositoryStats? = null,
  @Json(ignore = true)
  val endRepoStats: RepositoryStats? = null,
  @Json(ignore = true)
  val deleteOldArtifactsResult: DeleteOldArtifactsResult? = null,
  @Json(ignore = true)
  val deleteOldArtifactsDuration: Duration? = null,
  @Json(ignore = true)
  val deleteOldBomsResult: DeleteOldBomsResult? = null,
  @Json(ignore = true)
  val deleteOldBomsDuration: Duration? = null,
  @Json(ignore = true)
  val totalDuration: Duration? = null,
) {
  fun toArtifactRemoverEvent(): ArtifactRemoverEvent {
    return ArtifactRemoverEvent(
      result = result,
      startCountInstalledProjects = startRepoStats?.countInstalledProjects ?: -1,
      startCountInstalledArtifacts = startRepoStats?.countInstalledArtifacts ?: -1,
      startCountInstalledBoms = startRepoStats?.countInstalledBoms ?: -1,
      startSizeOfInstalledArtifactsBytes = startRepoStats?.sizeOfInstalledArtifactsBytes ?: -1,
      startSizeOfInstalledBomsBytes = startRepoStats?.sizeOfInstalledBomsBytes ?: -1,
      startOverallRepoSizeBytes = startRepoStats?.overallRepoSizeBytes ?: -1,
      startInstalledArtifactsMeasurementDurationMs = startRepoStats?.installedArtifactsMeasurementDuration?.inWholeMillisecondsIfFinite ?: -1,
      startInstalledBomsMeasurementDurationMs = startRepoStats?.installedBomsMeasurementDuration?.inWholeMillisecondsIfFinite ?: -1,
      startMeasureRepoDurationMs = startRepoStats?.measurementDuration?.inWholeMillisecondsIfFinite ?: -1,
      endInstalledArtifactsMeasurementDurationMs = endRepoStats?.installedArtifactsMeasurementDuration?.inWholeMillisecondsIfFinite ?: -1,
      endInstalledBomsMeasurementDurationMs = endRepoStats?.installedBomsMeasurementDuration?.inWholeMillisecondsIfFinite ?: -1,
      endMeasureRepoDurationMs = endRepoStats?.measurementDuration?.inWholeMillisecondsIfFinite ?: -1,
      endOverallRepoSizeBytes = endRepoStats?.overallRepoSizeBytes ?: -1,
      endSizeOfInstalledArtifactsBytes = endRepoStats?.sizeOfInstalledArtifactsBytes ?: -1,
      endSizeOfInstalledBomsBytes = endRepoStats?.sizeOfInstalledBomsBytes ?: -1,
      endCountInstalledProjects = endRepoStats?.countInstalledProjects ?: -1,
      endCountInstalledArtifacts = endRepoStats?.countInstalledArtifacts ?: -1,
      endCountInstalledBoms = endRepoStats?.countInstalledBoms ?: -1,
      deleteOldArtifactsResultAttemptedDeletionArtifacts = deleteOldArtifactsResult?.attemptedToDelete?.size?.toLong() ?: -1,
      deleteOldArtifactsResultDeletedArtifacts = deleteOldArtifactsResult?.successfulDeletion?.size?.toLong() ?: -1,
      deleteOldArtifactsResultFailedToDeleteArtifacts = deleteOldArtifactsResult?.failedDeletion?.size?.toLong() ?: -1,
      deleteOldArtifactsDurationMs = deleteOldArtifactsDuration?.inWholeMillisecondsIfFinite ?: -1,
      deleteOldBomsResultAttemptedDeletionBoms = deleteOldBomsResult?.attemptedDeletionBoms?.size?.toLong() ?: -1,
      deleteOldBomsResultDeletedBoms = deleteOldBomsResult?.successfulDeletionBoms?.size?.toLong() ?: -1,
      deleteOldBomsResultFailedToDeleteBoms = deleteOldBomsResult?.failedDeletionBoms?.size?.toLong() ?: -1,
      deleteOldBomsDurationMs = deleteOldBomsDuration?.inWholeMillisecondsIfFinite ?: -1,
      totalDurationMs = totalDuration?.inWholeMillisecondsIfFinite ?: -1
    )
  }
}

data class DeleteOldBomsResult(
  val attemptedDeletionBoms: Set<InstalledBom> = emptySet(),
  val successfulDeletionBoms: Set<InstalledBom> = emptySet(),
  val failedDeletionBoms: Set<InstalledBom> = emptySet(),
)

data class DeleteOldArtifactsResult(
  val attemptedToDelete: List<InstalledProject> = emptyList(),
  val successfulDeletion: List<InstalledProject> = emptyList(),
  val failedDeletion: List<InstalledProject> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ArtifactRemoverEvent(
  @Json(name = "result")
  val result: ArtifactRemoverEventResult = ArtifactRemoverEventResult.UNKNOWN,
  @Json(name = "repo_stats_start_count_installed_projects")
  val startCountInstalledProjects: Long = -1,
  @Json(name = "repo_stats_start_count_installed_artifacts")
  val startCountInstalledArtifacts: Long = -1,
  @Json(name = "repo_stats_start_count_boms_insalled")
  val startCountInstalledBoms: Long = -1,
  @Json(name = "repo_stats_start_size_of_installed_artifacts_bytes")
  val startSizeOfInstalledArtifactsBytes: Long = -1,
  @Json(name = "repo_stats_start_size_of_installed_boms_bytes")
  val startSizeOfInstalledBomsBytes: Long = -1,
  @Json(name = "repo_stats_start_overall_repo_size_bytes")
  val startOverallRepoSizeBytes: Long = -1,
  @Json(name = "repo_stats_start_installed_artifacts_measurement_duration_ms")
  val startInstalledArtifactsMeasurementDurationMs: Long = -1,
  @Json(name = "repo_stats_start_installed_boms_measurement_duration_ms")
  val startInstalledBomsMeasurementDurationMs: Long = -1,
  @Json(name = "repo_stats_start_measure_repo_duration_ms")
  val startMeasureRepoDurationMs: Long = -1,
  @Json(name = "repo_stats_end_installed_artifacts_measurement_duration_ms")
  val endInstalledArtifactsMeasurementDurationMs: Long = -1,
  @Json(name = "repo_stats_end_installed_boms_measurement_duration_ms")
  val endInstalledBomsMeasurementDurationMs: Long = -1,
  @Json(name = "repo_stats_end_measure_repo_duration_ms")
  val endMeasureRepoDurationMs: Long = -1,
  @Json(name = "repo_stats_end_overall_repo_size_bytes")
  val endOverallRepoSizeBytes: Long = -1,
  @Json(name = "repo_stats_end_size_of_installed_artifacts_bytes")
  val endSizeOfInstalledArtifactsBytes: Long = -1,
  @Json(name = "repo_stats_end_size_of_installed_boms_bytes")
  val endSizeOfInstalledBomsBytes: Long = -1,
  @Json(name = "repo_stats_end_count_installed_projects")
  val endCountInstalledProjects: Long = -1,
  @Json(name = "repo_stats_end_count_installed_artifacts")
  val endCountInstalledArtifacts: Long = -1,
  @Json(name = "repo_stats_end_count_boms_insalled")
  val endCountInstalledBoms: Long = -1,
  @Json(name = "count_artifacts_attempted_delete")
  val deleteOldArtifactsResultAttemptedDeletionArtifacts: Long = -1,
  @Json(name = "count_artifacts_successfully_deleted")
  val deleteOldArtifactsResultDeletedArtifacts: Long = -1,
  @Json(name = "count_artifacts_failed_to_delete")
  val deleteOldArtifactsResultFailedToDeleteArtifacts: Long = -1,
  @Json(name = "delete_old_artifacts_duration_ms")
  val deleteOldArtifactsDurationMs: Long = -1,
  @Json(name = "count_boms_attempted_delete")
  val deleteOldBomsResultAttemptedDeletionBoms: Long = -1,
  @Json(name = "count_boms_successfully_deleted")
  val deleteOldBomsResultDeletedBoms: Long = -1,
  @Json(name = "count_boms_failed_to_delete")
  val deleteOldBomsResultFailedToDeleteBoms: Long = -1,
  @Json(name = "delete_old_boms_duration_ms")
  val deleteOldBomsDurationMs: Long = -1,
  @Json(name = "total_duration_ms")
  val totalDurationMs: Long = -1,
  @Json(name = "user_ldap")
  val userLdap: String = System.getProperty("user.name")
  ) {

  fun toEventStreamEvent(): EventstreamEvent {
    return EventstreamEvent(
      catalogName = "artifact_sync_artifact_remover",
      appName = "artifact_sync",
      event = this
    )
  }
}
