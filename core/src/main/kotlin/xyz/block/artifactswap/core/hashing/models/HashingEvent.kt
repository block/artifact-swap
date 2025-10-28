package xyz.block.artifactswap.core.hashing.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import xyz.block.artifactswap.core.eventstream.EventstreamEvent
import kotlin.time.Duration

/**
 * Result of hashing a single project.
 */
data class ProjectHashingResult(
    val projectPath: String,
    val hash: String,
    val duration: Duration,
    val countFilesHashed: Int,
)

/**
 * Overall result of the hashing operation.
 */
data class HashingServiceResult(
    val totalDurationMs: Long = -1,
    val acquireProjectsDurationMs: Long = -1,
    val hashProjectsDurationMs: Long = -1,
    val countProjectsHashed: Int = -1,
    val includedProjects: Set<String> = emptySet(),
    val countProjectsIncluded: Int = -1,
    val countFilesHashed: Int = -1,
    val gitBranch: String = "",
    val gitSha: String = "",
    val ciEnv: String = "",
    val buildId: String = "",
    val buildStepId: String = "",
    val buildJobId: String = "",
    val ciType: String = "",
    val userLdap: String = System.getProperty("user.name"),
    val projectHashes: List<ProjectHashingResult> = emptyList()
)

/**
 * Event sent to eventstream for analytics.
 */
@JsonClass(generateAdapter = true)
data class HashingExecutionEvent(
    @Json(name = "total_duration_ms")
    val totalDurationMs: Long = -1,
    @Json(name = "acquire_projects_duration_ms")
    val acquireProjectsDurationMs: Long = -1,
    @Json(name = "hash_projects_duration_ms")
    val hashProjectsDurationMs: Long = -1,
    @Json(name = "count_projects_hashed")
    val countProjectsHashed: Int = -1,
    @Json(ignore = true)
    val includedProjects: Set<String> = emptySet(),
    @Json(name = "count_projects_included")
    val countProjectsIncluded: Int = -1,
    @Json(name = "count_files_hashed")
    val countFilesHashed: Int = -1,
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
            catalogName = "artifact_sync_hashing",
            appName = "artifact_sync",
            event = this
        )
    }
}

/**
 * Converts a HashingServiceResult to an event for analytics.
 */
fun HashingServiceResult.toEvent(): HashingExecutionEvent {
    return HashingExecutionEvent(
        totalDurationMs = totalDurationMs,
        acquireProjectsDurationMs = acquireProjectsDurationMs,
        hashProjectsDurationMs = hashProjectsDurationMs,
        countProjectsHashed = countProjectsHashed,
        includedProjects = includedProjects,
        countProjectsIncluded = countProjectsIncluded,
        countFilesHashed = countFilesHashed,
        gitBranch = gitBranch,
        gitSha = gitSha,
        ciEnv = ciEnv,
        buildId = buildId,
        buildStepId = buildStepId,
        buildJobId = buildJobId,
        ciType = ciType,
        userLdap = userLdap
    )
}
