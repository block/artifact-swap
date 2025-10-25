package xyz.block.artifactswap.core.publisher.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import xyz.block.artifactswap.core.eventstream.EventstreamEvent

/**
 * Documents final task status.
 *
 * Failures also denote the general stages of execution for the task.
 */
enum class BomPublishingResult {
    SUCCESS_BOM_AND_METADATA_PUBLISHED, // new bom and metadata published successfully
    SUCCESS_BOM_PUBLISHED_METADATA_NO_UPDATE, // pom published, but metadata was same as before
    SUCCESS_BOM_PUBLISHED_METADATA_FAILED, // bom published, tried to publish metadata but failed
    FAILED_READING_PROJECT_HASHES,
    FAILED_FETCHING_PUBLISHED_PROJECT_DATA,
    FAILED_PUBLISHING_UPDATED_POM,
    UNKNOWN
}

/**
 * Result of a BOM publishing operation.
 */
data class BomPublisherResult(
    val result: BomPublishingResult = BomPublishingResult.UNKNOWN,
    val totalDurationMs: Long? = null,
    val readHashedProjectsDurationMs: Long? = null,
    val requestProjectDataArtifactoryDurationMs: Long? = null,
    val publishUpdatedBomAndMetadataDurationMs: Long? = null,
    val countProjectsHashed: Long? = null,
    val countProjectsInArtifactory: Long? = null,
    val countProjectsIncludedInBom: Long? = null,
    val gitBranch: String = "",
    val gitSha: String = "",
    val kochikuEnv: String = "",
    val buildId: String = "",
    val buildStepId: String = "",
    val buildJobId: String = "",
    val ciType: String = ""
)

/**
 * Event sent to eventstream for analytics.
 */
@JsonClass(generateAdapter = true)
data class BomPublishingEvent(
    @Json(name = "result")
    val result: BomPublishingResult? = null,
    @Json(name = "total_duration_ms")
    val totalDurationMs: Long? = null,
    @Json(name = "read_hashed_projects_duration_ms")
    val readHashedProjectsDurationMs: Long? = null,
    @Json(name = "request_project_data_artifactory_duration_ms")
    val requestProjectDataArtifactoryDurationMs: Long? = null,
    @Json(name = "publish_updated_bom_and_metadata_duration_ms")
    val publishUpdatedBomAndMetadataDurationMs: Long? = null,
    @Json(name = "count_projects_hashed")
    val countProjectsHashed: Long? = null,
    @Json(name = "count_projects_in_artifactory")
    val countProjectsInArtifactory: Long? = null,
    @Json(name = "count_projects_included_in_bom")
    val countProjectsIncludedInBom: Long? = null,
    @Json(name = "base_git_branch")
    val gitBranch: String,
    @Json(name = "base_git_sha")
    val gitSha: String,
    @Json(name = "ci_env")
    val kochikuEnv: String,
    @Json(name = "ci_build_id")
    val buildId: String,
    @Json(name = "ci_build_step_id")
    val buildStepId: String,
    @Json(name = "ci_build_job_id")
    val buildJobId: String,
    @Json(name = "ci_type")
    val ciType: String,
) {
    fun toEventStreamEvent(): EventstreamEvent {
        return EventstreamEvent(
            catalogName = "artifact_sync_bom_publishing",
            appName = "artifact_sync",
            event = this
        )
    }
}

/**
 * Converts a BomPublisherResult to an event for analytics.
 */
fun BomPublisherResult.toEvent(): BomPublishingEvent {
    return BomPublishingEvent(
        result = result,
        totalDurationMs = totalDurationMs,
        readHashedProjectsDurationMs = readHashedProjectsDurationMs,
        requestProjectDataArtifactoryDurationMs = requestProjectDataArtifactoryDurationMs,
        publishUpdatedBomAndMetadataDurationMs = publishUpdatedBomAndMetadataDurationMs,
        countProjectsHashed = countProjectsHashed,
        countProjectsInArtifactory = countProjectsInArtifactory,
        countProjectsIncludedInBom = countProjectsIncludedInBom,
        gitBranch = gitBranch,
        gitSha = gitSha,
        kochikuEnv = kochikuEnv,
        buildId = buildId,
        buildStepId = buildStepId,
        buildJobId = buildJobId,
        ciType = ciType
    )
}
