package xyz.block.artifactswap.cli.commands

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.kotlin.logger
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import picocli.CommandLine
import xyz.block.artifactswap.cli.AbstractArtifactSwapCommand
import xyz.block.artifactswap.cli.options.CiConfigurationOptions
import xyz.block.artifactswap.cli.options.OutputMode
import xyz.block.artifactswap.cli.options.TaskFinderOptions
import xyz.block.artifactswap.core.task_finder.CiMetadata
import xyz.block.artifactswap.core.task_finder.TaskFinderService
import xyz.block.artifactswap.core.task_finder.di.TaskFinderServiceConfig
import xyz.block.artifactswap.core.task_finder.di.taskFinderService
import xyz.block.artifactswap.core.task_finder.di.taskFinderServiceModules
import xyz.block.artifactswap.core.task_finder.models.TaskFinderResult
import xyz.block.artifactswap.core.task_finder.models.TaskFinderServiceResult
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.io.path.appendBytes
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.notExists
import kotlin.io.path.outputStream

/**
 * CLI command for finding Gradle tasks across all projects.
 */
@CommandLine.Command(
    name = "task-finder",
    description = ["Generates the list of publishing tasks"]
)
class TaskFinderCommand : AbstractArtifactSwapCommand() {

    @CommandLine.Mixin
    private lateinit var taskFinderOptions: TaskFinderOptions

    @CommandLine.Mixin
    private lateinit var ciOptions: CiConfigurationOptions

    private lateinit var taskFinderService: TaskFinderService
    private lateinit var ioDispatcher: CoroutineDispatcher

    override fun init(application: KoinApplication) {
        val config = TaskFinderServiceConfig()
        application.modules(taskFinderServiceModules(application, config))
    }

    override suspend fun executeCommand(application: KoinApplication) {
        taskFinderService = application.taskFinderService
        ioDispatcher = application.koin.get(named("IO"))

        val applicationDirectory = application.koin.get<Path>(named("directory"))

        var result: TaskFinderServiceResult? = null

        try {
            val ciMetadata = CiMetadata(
                gitBranch = System.getenv("GIT_BRANCH").orEmpty(),
                gitSha = System.getenv("GIT_COMMIT").orEmpty(),
                ciEnv = System.getenv("KOCHIKU_ENV").orEmpty(),
                buildId = ciOptions.buildId,
                buildStepId = ciOptions.buildStepId,
                buildJobId = ciOptions.buildJobId,
                ciType = ciOptions.ciType
            )

            // Get Gradle arguments from the application
            val gradleArgs = application.koin.getOrNull<List<String>>(named("gradleArgs")) ?: emptyList()
            val gradleJvmArgs = application.koin.getOrNull<List<String>>(named("gradleJvmArgs")) ?: emptyList()

            // Find all tasks matching the task name
            val taskFindingResult = taskFinderService.findTasks(
                applicationDirectory = applicationDirectory,
                taskName = taskFinderOptions.task,
                gradleArgs = gradleArgs,
                gradleJvmArgs = gradleJvmArgs,
                ciMetadata = ciMetadata
            )

            // Write tasks to files based on output mode
            val updatedResult = writeTasksToFiles(taskFindingResult.serviceResult, taskFindingResult.tasks.map { it.path })
            result = updatedResult

            // Log the result
            taskFinderService.logResult(updatedResult)
        } catch (e: Exception) {
            // Log failure result if we have it
            result?.let { taskFinderService.logResult(it.copy(result = TaskFinderResult.FAILURE)) }
            throw e
        }
    }

    /**
     * Writes tasks to files based on the output mode.
     */
    private suspend fun writeTasksToFiles(
        serviceResult: TaskFinderServiceResult,
        taskPaths: List<String>
    ): TaskFinderServiceResult {
        return when (taskFinderOptions.outputMode) {
            OutputMode.SINGLE_TASK_LIST -> writeSingleTaskList(serviceResult, taskPaths)
            OutputMode.SHARD_TASK_LIST -> writeShardedTaskList(serviceResult, taskPaths)
        }
    }

    /**
     * Writes all tasks to a single file.
     */
    private suspend fun writeSingleTaskList(
        serviceResult: TaskFinderServiceResult,
        taskPaths: List<String>
    ): TaskFinderServiceResult {
        withContext(ioDispatcher) {
            taskFinderOptions.taskListOutputDirectory
                .createDirectories()
                .resolve("task-output.out")
                .createFile()
                .outputStream().buffered().use { os ->
                    taskPaths.forEach { taskPath ->
                        os.write(taskPath.toByteArray())
                        os.write('\n'.code)
                    }
                    os.flush()
                }
        }

        return serviceResult.copy(
            countTasksInLastOutputFile = taskPaths.size
        )
    }

    /**
     * Writes tasks to multiple files based on pagination and chunking.
     */
    private suspend fun writeShardedTaskList(
        serviceResult: TaskFinderServiceResult,
        taskPaths: List<String>
    ): TaskFinderServiceResult {
        var pageCount = 0
        var tasksInLastOutputFile = 0
        val byteArrayOutputStream = ByteArrayOutputStream()

        taskPaths.forEachIndexed { index, taskPath ->
            // Track tasks in last output file
            tasksInLastOutputFile = if (pageCount == taskFinderOptions.pages - 1) {
                tasksInLastOutputFile + 1
            } else {
                tasksInLastOutputFile
            }

            // Create a new file after each chunk amount is reached, and up to page limit
            // NOTE: If there are more tasks left, but no pages,
            // left over tasks will be dumped on last page
            if (
                index % taskFinderOptions.chunks == 0 &&
                pageCount < taskFinderOptions.pages &&
                byteArrayOutputStream.size() != 0
            ) {
                // Flush the bytearray to a new file
                withContext(ioDispatcher) {
                    taskFinderOptions.taskListOutputDirectory
                        .createDirectories()
                        .resolve((++pageCount).createTaskOutputFileName())
                        .createFile()
                        .outputStream().buffered().use { os ->
                            byteArrayOutputStream.writeTo(os)
                            os.flush()
                        }
                }
                byteArrayOutputStream.reset() // Reset the output stream
            }

            // Buffer the task to the output stream
            byteArrayOutputStream.write(taskPath.toByteArray())
            byteArrayOutputStream.write('\n'.code)
        }

        // Write remaining tasks
        if (byteArrayOutputStream.size() != 0) {
            withContext(ioDispatcher) {
                taskFinderOptions.taskListOutputDirectory
                    .createDirectories()
                    .resolve((pageCount).createTaskOutputFileName())
                    .apply {
                        if (notExists()) {
                            createFile()
                        }
                    }
                    .appendBytes(byteArrayOutputStream.toByteArray())
            }
        }

        return serviceResult.copy(
            countTasksInLastOutputFile = tasksInLastOutputFile
        )
    }

    private fun Int.createTaskOutputFileName(): String {
        return "task-output-$this.out"
    }
}
