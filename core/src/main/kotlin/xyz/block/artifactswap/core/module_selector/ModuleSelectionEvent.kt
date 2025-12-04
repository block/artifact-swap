package xyz.block.artifactswap.core.module_selector

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlin.time.Duration
import xyz.block.artifactswap.core.eventstream.EventstreamEvent

enum class ModuleSelectionEventResult {
  UNSET,
  SUCCESS,
  FAILED_DETERMINING_BOM_VERSION,
  FAILED_READING_LOCAL_GIT_CHANGES,
  FAILED_READING_LOCAL_ARTIFACTS,
}

/**
 * Records execution data for artifact sync module selection, logged to ES2.
 *
 * Tracks which projects were included in the build and why, along with timing information.
 */
@JsonClass(generateAdapter = true)
data class ModuleSelectionEvent(
  @Json(name = "result") val result: ModuleSelectionEventResult = ModuleSelectionEventResult.UNSET,
  @Json(name = "acquire_projects_duration_ms") val acquireProjectsDurationMs: Long = -1,
  @Json(name = "determine_bom_version_duration_ms") val determineBomVersionDurationMs: Long = -1,
  @Json(name = "git_determine_local_changes_duration_ms")
  val gitDetermineLocalChangesDurationMs: Long = -1,
  @Json(name = "determine_local_artifacts_duration_ms")
  val determineLocalArtifactsDurationMs: Long = -1,
  @Json(name = "determine_included_projects_duration_ms")
  val determineIncludedProjectsDurationMs: Long = -1,
  @Json(name = "total_duration_ms") val totalDurationMs: Long = -1,
  @Json(name = "count_gradle_projects_considered") val countGradleProjectsConsidered: Int = -1,
  @Json(name = "count_projects_included") val countProjectsIncluded: Int = -1,
  @Json(name = "count_projects_included_due_to_project_requested")
  val countProjectsIncludedDueToProjectRequested: Int = -1,
  @Json(name = "count_projects_included_due_to_no_artifact_to_swap")
  val countProjectsIncludedDueToNoArtifactToSwap: Int = -1,
  @Json(name = "count_projects_included_due_to_files_changed_in_module")
  val countProjectsIncludedDueToFilesChangedInModule: Int = -1,
  @Json(name = "count_git_local_files_changed") val locallyChangedFilesCount: Int = -1,
  @Json(name = "bom_version") val bomVersionUsed: String = "",
  @Json(name = "count_local_artifacts_available_for_swapping")
  val countLocalArtifactsAvailableForSwapping: Int = -1,
  @Json(name = "user_ldap") val userLdap: String = System.getProperty("user.name"),
) {
  /**
   * Convenience constructor to create a success event from selection computation result.
   *
   * @param result The selection computation result containing all selection data
   * @param totalDuration Total duration of the entire selection process
   */
  internal constructor(
    result: SelectionComputationResult,
    totalDuration: Duration,
  ) : this(
    result = ModuleSelectionEventResult.SUCCESS,
    determineBomVersionDurationMs = result.bomDuration.inWholeMilliseconds,
    bomVersionUsed = result.bomVersionToUse,
    gitDetermineLocalChangesDurationMs = result.gitDuration.inWholeMilliseconds,
    locallyChangedFilesCount = result.locallyChangedFilesCount,
    determineLocalArtifactsDurationMs = result.artifactsDuration.inWholeMilliseconds,
    countLocalArtifactsAvailableForSwapping = result.swappableArtifactsCount,
    countGradleProjectsConsidered = result.metrics.totalCandidates,
    countProjectsIncluded = result.metrics.totalSelected,
    countProjectsIncludedDueToProjectRequested = result.metrics.selectedDueToExplicitRequest,
    countProjectsIncludedDueToFilesChangedInModule = result.metrics.selectedDueToLocalChanges,
    countProjectsIncludedDueToNoArtifactToSwap = result.metrics.selectedDueToMissingArtifact,
    determineIncludedProjectsDurationMs = result.selectionDuration.inWholeMilliseconds,
    totalDurationMs = totalDuration.inWholeMilliseconds,
  )

  fun toEventStreamEvent(): EventstreamEvent {
    return EventstreamEvent(
      catalogName = "artifact_sync_module_selection",
      appName = "artifact_sync",
      event = this,
    )
  }
}
