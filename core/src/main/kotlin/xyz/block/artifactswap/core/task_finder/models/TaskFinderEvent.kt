package xyz.block.artifactswap.core.task_finder.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Result of task finder operation.
 */
enum class TaskFinderResult {
    SUCCESS,
    FAILURE,
    NONE
}

/**
 * Result of a task finding operation.
 */
data class TaskFinderServiceResult(
    val result: TaskFinderResult = TaskFinderResult.NONE,
    val taskName: String = "",
    val countTasksWithName: Int = -1,
    val countTasksInLastOutputFile: Int = -1,
    val gradleProjectConnectionDurationMs: Long = -1,
    val gradleTaskFindingDurationMs: Long = -1,
    val totalDurationMs: Long = -1,
    val gitBranch: String = "",
    val gitSha: String = "",
    val ciEnv: String = "",
    val buildId: String = "",
    val buildStepId: String = "",
    val buildJobId: String = "",
    val ciType: String = ""
)

/**
 * Event for task finder analytics.
 */
@JsonClass(generateAdapter = true)
data class TaskFinderExecutionEvent(
    @Json(name = "result")
    val result: TaskFinderResult = TaskFinderResult.NONE,
    @Json(name = "task_name")
    val taskName: String = "",
    @Json(name = "count_tasks_with_name")
    val countTasksWithName: Int = -1,
    @Json(name = "count_tasks_in_last_output_file")
    val countTasksInLastOutputFile: Int = -1,
    @Json(name = "gradle_project_connection_duration_ms")
    val gradleProjectConnectionDurationMs: Long = -1,
    @Json(name = "gradle_task_finding_duration_ms")
    val gradleTaskFindingDurationMs: Long = -1,
    @Json(name = "total_duration_ms")
    val totalDurationMs: Long = -1,
    @Json(name = "git_branch")
    val gitBranch: String = "",
    @Json(name = "git_sha")
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
fun TaskFinderServiceResult.toEvent(): TaskFinderExecutionEvent {
    return TaskFinderExecutionEvent(
        result = result,
        taskName = taskName,
        countTasksWithName = countTasksWithName,
        countTasksInLastOutputFile = countTasksInLastOutputFile,
        gradleProjectConnectionDurationMs = gradleProjectConnectionDurationMs,
        gradleTaskFindingDurationMs = gradleTaskFindingDurationMs,
        totalDurationMs = totalDurationMs,
        gitBranch = gitBranch,
        gitSha = gitSha,
        ciEnv = ciEnv,
        buildId = buildId,
        buildStepId = buildStepId,
        buildJobId = buildJobId,
        ciType = ciType
    )
}
