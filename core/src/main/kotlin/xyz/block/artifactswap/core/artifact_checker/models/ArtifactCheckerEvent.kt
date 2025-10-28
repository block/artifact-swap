package xyz.block.artifactswap.core.artifact_checker.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Result of artifact checker operation.
 */
enum class ArtifactCheckerResult {
    SUCCESS,
    FAILED_NO_INPUT_FILE,
    FAILED_READING_INPUT_FILES,
    FAILED_WRITING_OUTPUT,
    UNKNOWN
}

/**
 * Result of an artifact checking operation.
 */
data class ArtifactCheckerServiceResult(
    val result: ArtifactCheckerResult = ArtifactCheckerResult.UNKNOWN,
    val countProjectsToCheck: Int = -1,
    val countArtifactsFound: Int = -1,
    val totalDurationMs: Long = -1,
    val determineProjectsDurationMs: Long = -1,
    val checkArtifactoryDurationMs: Long = -1,
    val gitBranch: String = "",
    val gitSha: String = "",
    val ciEnv: String = "",
    val buildId: String = "",
    val buildStepId: String = "",
    val buildJobId: String = "",
    val ciType: String = "",
    val missingArtifacts: List<String> = emptyList()
)

/**
 * Event for artifact checker analytics.
 */
@JsonClass(generateAdapter = true)
data class ArtifactCheckerExecutionEvent(
    @Json(name = "result")
    val result: ArtifactCheckerResult = ArtifactCheckerResult.UNKNOWN,
    @Json(name = "count_projects_to_check")
    val countProjectsToCheck: Int = -1,
    @Json(name = "count_artifacts_found")
    val countArtifactsFound: Int = -1,
    @Json(name = "total_duration_ms")
    val totalDurationMs: Long = -1,
    @Json(name = "determine_projects_duration_ms")
    val determineProjectsDurationMs: Long = -1,
    @Json(name = "check_artifactory_duration_ms")
    val checkArtifactoryDurationMs: Long = -1,
    @Json(name = "ci_git_branch")
    val gitBranch: String = "",
    @Json(name = "ci_git_sha")
    val gitSha: String = "",
    @Json(name = "ci_env")
    val ciEnv: String = "",
    @Json(name = "ci_build_id")
    val buildId: String = "",
    @Json(name = "ci_build_step_id")
    val buildStepId: String = "",
    @Json(name = "ci_build_job_id")
    val buildJobId: String = "",
    @Json(name = "ci_type")
    val ciType: String = ""
)

/**
 * Converts a service result to an execution event.
 */
fun ArtifactCheckerServiceResult.toEvent(): ArtifactCheckerExecutionEvent {
    return ArtifactCheckerExecutionEvent(
        result = result,
        countProjectsToCheck = countProjectsToCheck,
        countArtifactsFound = countArtifactsFound,
        totalDurationMs = totalDurationMs,
        determineProjectsDurationMs = determineProjectsDurationMs,
        checkArtifactoryDurationMs = checkArtifactoryDurationMs,
        gitBranch = gitBranch,
        gitSha = gitSha,
        ciEnv = ciEnv,
        buildId = buildId,
        buildStepId = buildStepId,
        buildJobId = buildJobId,
        ciType = ciType
    )
}
