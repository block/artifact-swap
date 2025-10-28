package xyz.block.artifactswap.core.task_finder

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.kotlin.logger
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.GradleTask
import xyz.block.artifactswap.core.task_finder.models.TaskFinderResult
import xyz.block.artifactswap.core.task_finder.models.TaskFinderServiceResult
import xyz.block.artifactswap.core.task_finder.services.TaskFinderEventStream
import java.nio.file.Path
import java.time.Clock
import kotlin.time.measureTimedValue

/**
 * Service for finding Gradle tasks across all projects.
 */
class TaskFinderService(
    private val eventStream: TaskFinderEventStream,
    private val ioDispatcher: CoroutineDispatcher
) {
    /**
     * Finds all tasks with the given name across all Gradle projects.
     *
     * @param applicationDirectory Root directory of the Gradle project
     * @param taskName Name of the task to find
     * @param gradleArgs Additional Gradle arguments
     * @param gradleJvmArgs JVM arguments for Gradle
     * @param ciMetadata CI metadata for analytics
     * @return Result containing the list of task paths and analytics data
     */
    suspend fun findTasks(
        applicationDirectory: Path,
        taskName: String,
        gradleArgs: List<String>,
        gradleJvmArgs: List<String>,
        ciMetadata: CiMetadata
    ): TaskFindingResult {
        val startTime = Clock.systemUTC().millis()
        var result = TaskFinderServiceResult(
            taskName = taskName,
            gitBranch = ciMetadata.gitBranch,
            gitSha = ciMetadata.gitSha,
            ciEnv = ciMetadata.ciEnv,
            buildId = ciMetadata.buildId,
            buildStepId = ciMetadata.buildStepId,
            buildJobId = ciMetadata.buildJobId,
            ciType = ciMetadata.ciType
        )

        val projectConnection = GradleConnector.newConnector()
            .forProjectDirectory(applicationDirectory.toFile())
            .connect()

        try {
            logger.info { "Grabbing Gradle project" }

            val (rootProject, getGradleProjectDuration) = measureTimedValue {
                withContext(ioDispatcher) {
                    projectConnection.model(GradleProject::class.java)
                        .addArguments(*gradleArgs.toTypedArray())
                        .addJvmArguments(*gradleJvmArgs.toTypedArray())
                        .get()
                }
            }
            logger.info { "Got Gradle project" }
            result = result.copy(
                gradleProjectConnectionDurationMs = getGradleProjectDuration.inWholeMilliseconds
            )

            // Collect all tasks matching the given name
            val (tasks, taskFindingDuration) = measureTimedValue {
                collectAllTasks(rootProject)
                    .filter { it.path.endsWith(taskName) }
            }

            result = result.copy(
                countTasksWithName = tasks.size,
                gradleTaskFindingDurationMs = taskFindingDuration.inWholeMilliseconds,
                result = TaskFinderResult.SUCCESS
            )

            return TaskFindingResult(
                tasks = tasks,
                serviceResult = result.copy(
                    totalDurationMs = Clock.systemUTC().millis() - startTime
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Error executing task finder" }
            result = result.copy(
                result = TaskFinderResult.FAILURE,
                totalDurationMs = Clock.systemUTC().millis() - startTime
            )
            throw e
        } finally {
            projectConnection.close()
        }
    }

    /**
     * Logs the result to the event stream.
     */
    suspend fun logResult(result: TaskFinderServiceResult) {
        eventStream.sendResults(listOf(result))
    }

    /**
     * Recursively collects all tasks from a Gradle project and its children.
     */
    private fun collectAllTasks(project: GradleProject): List<GradleTask> {
        return buildList {
            addAll(project.tasks)
            project.children.forEach { child ->
                addAll(collectAllTasks(child))
            }
        }
    }
}

/**
 * Result of finding tasks, containing both the task list and analytics data.
 */
data class TaskFindingResult(
    val tasks: List<GradleTask>,
    val serviceResult: TaskFinderServiceResult
)

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
