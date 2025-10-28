package xyz.block.artifactswap.core.task_runner.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Result of task runner operation.
 */
enum class TaskRunnerResult {
    /**
     * All tasks were run to completion (note: not all necessarily succeeded).
     */
    SUCCESS,

    /**
     * Unable to complete execution of all tasks.
     */
    FAILURE,

    /**
     * No tasks found in input.
     */
    NO_OP,

    /**
     * Default value, used before we know final result.
     */
    NONE
}

/**
 * Result of a task running operation.
 */
data class TaskRunnerServiceResult(
    val result: TaskRunnerResult = TaskRunnerResult.NONE,
    val countTasksToRun: Int = -1,
    val countTasksSucceeded: Int = -1,
    val countTasksFailed: Int = -1,
    val taskExecutionDurationMs: Long = -1,
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
 * Event for task runner analytics.
 */
@JsonClass(generateAdapter = true)
data class TaskRunnerExecutionEvent(
    @Json(name = "result")
    val result: TaskRunnerResult = TaskRunnerResult.NONE,
    @Json(name = "count_tasks_to_run")
    val countTasksToRun: Int = -1,
    @Json(name = "count_tasks_succeeded")
    val countTasksSucceeded: Int = -1,
    @Json(name = "count_tasks_failed")
    val countTasksFailed: Int = -1,
    @Json(name = "task_execution_duration_ms")
    val taskExecutionDurationMs: Long = -1,
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
fun TaskRunnerServiceResult.toEvent(): TaskRunnerExecutionEvent {
    return TaskRunnerExecutionEvent(
        result = result,
        countTasksToRun = countTasksToRun,
        countTasksSucceeded = countTasksSucceeded,
        countTasksFailed = countTasksFailed,
        taskExecutionDurationMs = taskExecutionDurationMs,
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
