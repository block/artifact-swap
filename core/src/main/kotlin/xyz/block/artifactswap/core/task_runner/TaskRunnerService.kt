package xyz.block.artifactswap.core.task_runner

import kotlinx.coroutines.CoroutineDispatcher
import org.apache.logging.log4j.kotlin.logger
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSuccessResult
import xyz.block.artifactswap.core.task_runner.models.TaskRunnerResult
import xyz.block.artifactswap.core.task_runner.models.TaskRunnerServiceResult
import xyz.block.artifactswap.core.task_runner.services.TaskRunnerEventStream
import java.nio.file.Path
import java.time.Clock
import kotlin.time.measureTime

/**
 * Service for running Gradle tasks and tracking their execution.
 */
class TaskRunnerService(
    private val eventStream: TaskRunnerEventStream,
    private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val CONTINUE_GRADLE_FLAG = "--continue"
        private const val DRY_RUN_FLAG = "--dry-run"
    }

    /**
     * Runs the given Gradle tasks and tracks their execution.
     *
     * @param applicationDirectory Root directory of the Gradle project
     * @param tasks List of task paths to execute
     * @param gradleArgs Additional Gradle arguments
     * @param gradleJvmArgs JVM arguments for Gradle
     * @param dryRun If true, runs Gradle in dry-run mode
     * @param ciMetadata CI metadata for analytics
     * @return Result containing execution statistics and analytics data
     */
    suspend fun runTasks(
        applicationDirectory: Path,
        tasks: List<String>,
        gradleArgs: List<String>,
        gradleJvmArgs: List<String>,
        dryRun: Boolean,
        ciMetadata: CiMetadata
    ): TaskRunnerServiceResult {
        val startTime = Clock.systemUTC().millis()
        var result = TaskRunnerServiceResult(
            countTasksToRun = tasks.size,
            gitBranch = ciMetadata.gitBranch,
            gitSha = ciMetadata.gitSha,
            ciEnv = ciMetadata.ciEnv,
            buildId = ciMetadata.buildId,
            buildStepId = ciMetadata.buildStepId,
            buildJobId = ciMetadata.buildJobId,
            ciType = ciMetadata.ciType
        )

        // If there are no tasks, return NO_OP immediately
        if (tasks.isEmpty()) {
            return result.copy(
                countTasksToRun = 0,
                result = TaskRunnerResult.NO_OP,
                totalDurationMs = Clock.systemUTC().millis() - startTime
            )
        }

        val projectConnection = GradleConnector.newConnector()
            .forProjectDirectory(applicationDirectory.toFile())
            .connect()

        try {
            // Collector of successful tasks
            val successfulTasks = hashSetOf<String>()

            // Execute tasks and measure duration
            val taskDuration = measureTime {
                try {
                    val buildLauncher = projectConnection.newBuild()
                        .forTasks(*tasks.toTypedArray())
                        .addProgressListener(
                            ProgressListener {
                                // We only care about completed tasks, skip everything else
                                val event = it as? TaskFinishEvent ?: return@ProgressListener

                                // Skip non-successful task result events
                                if (event.result !is TaskSuccessResult) return@ProgressListener

                                // If the task finished successfully, add it to our set of completed tasks
                                successfulTasks.add(event.descriptor.taskPath)
                            }
                        )
                        .addArguments(*(gradleArgs.toSet() + CONTINUE_GRADLE_FLAG).toTypedArray())
                        .addJvmArguments(*gradleJvmArgs.toTypedArray())

                    if (dryRun) {
                        buildLauncher.addArguments(DRY_RUN_FLAG)
                    }

                    buildLauncher.run()
                } catch (e: Throwable) {
                    // Stop the CI classifier from flagging this as a shard failure
                    if (e is GradleConnectionException &&
                        e.cause?.message?.contains("Gradle build daemon disappeared unexpectedly") == true
                    ) {
                        logger.error { "Task runner unable to continue due to Gradle daemon being killed" }
                    } else {
                        logger.error(e) { "Error executing tasks" }
                    }
                    result = result.copy(result = TaskRunnerResult.FAILURE)
                }
            }

            // Find the tasks that did not complete
            val incompleteTasks = tasks.toSet() - successfulTasks
            if (incompleteTasks.isNotEmpty()) {
                logger.warn { "The following tasks were not completed:" }
                incompleteTasks.forEach { task ->
                    logger.warn { task }
                }
            }

            result = result.copy(
                taskExecutionDurationMs = taskDuration.inWholeMilliseconds,
                countTasksSucceeded = successfulTasks.size,
                countTasksFailed = incompleteTasks.size,
                totalDurationMs = Clock.systemUTC().millis() - startTime,
                result = if (result.result == TaskRunnerResult.NONE) TaskRunnerResult.SUCCESS else result.result
            )

            return result
        } finally {
            projectConnection.close()
        }
    }

    /**
     * Logs the result to the event stream.
     */
    suspend fun logResult(result: TaskRunnerServiceResult) {
        eventStream.sendResults(listOf(result))
    }
}

/**
 * CI metadata for analytics.
 */
data class CiMetadata(
    val gitBranch: String = System.getenv("GIT_BRANCH").orEmpty(),
    val gitSha: String = System.getenv("GIT_COMMIT").orEmpty(),
    val ciEnv: String = System.getenv("KOCHIKU_ENV").orEmpty(),
    val buildId: String = "",
    val buildStepId: String = "",
    val buildJobId: String = "",
    val ciType: String = ""
)
