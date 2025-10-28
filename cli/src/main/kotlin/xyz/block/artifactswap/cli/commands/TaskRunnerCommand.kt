package xyz.block.artifactswap.cli.commands

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.kotlin.logger
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import picocli.CommandLine
import xyz.block.artifactswap.cli.AbstractArtifactSwapCommand
import xyz.block.artifactswap.cli.options.CiConfigurationOptions
import xyz.block.artifactswap.cli.options.TaskRunnerOptions
import xyz.block.artifactswap.core.task_runner.CiMetadata
import xyz.block.artifactswap.core.task_runner.TaskRunnerService
import xyz.block.artifactswap.core.task_runner.di.TaskRunnerServiceConfig
import xyz.block.artifactswap.core.task_runner.di.taskRunnerService
import xyz.block.artifactswap.core.task_runner.di.taskRunnerServiceModules
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.notExists
import kotlin.io.path.readLines

/**
 * CLI command for running Gradle tasks from a file.
 */
@CommandLine.Command(
    name = "task-runner",
    description = ["Runs the given list of tasks, outputting the projects that failed"]
)
class TaskRunnerCommand : AbstractArtifactSwapCommand() {

    @CommandLine.Mixin
    private lateinit var taskRunnerOptions: TaskRunnerOptions

    @CommandLine.Mixin
    private lateinit var ciOptions: CiConfigurationOptions

    private lateinit var taskRunnerService: TaskRunnerService
    private lateinit var ioDispatcher: CoroutineDispatcher

    override fun init(application: KoinApplication) {
        val config = TaskRunnerServiceConfig()
        application.modules(taskRunnerServiceModules(application, config))
    }

    override suspend fun executeCommand(application: KoinApplication) {
        taskRunnerService = application.taskRunnerService
        ioDispatcher = application.koin.get(named("IO"))

        val applicationDirectory = application.koin.get<Path>(named("directory"))

        // Read tasks from input file
        val tasks = withContext(ioDispatcher) {
            val inputPath = taskRunnerOptions.taskRunFile.normalize()

            assert(!inputPath.isDirectory()) { "Input file is a directory! $inputPath" }
            if (inputPath.notExists()) {
                logger.warn { "$inputPath does not exist." }
                return@withContext emptyList()
            }
            return@withContext inputPath.readLines()
        }

        val ciMetadata = CiMetadata(
            gitBranch = System.getenv("GIT_BRANCH").orEmpty(),
            gitSha = System.getenv("GIT_COMMIT").orEmpty(),
            ciEnv = System.getenv("KOCHIKU_ENV").orEmpty(),
            buildId = ciOptions.buildId,
            buildStepId = ciOptions.buildStepId,
            buildJobId = ciOptions.buildJobId,
            ciType = ciOptions.ciType
        )

        // Get Gradle arguments and dry-run flag from the application
        val gradleArgs = application.koin.getOrNull<List<String>>(named("gradleArgs")) ?: emptyList()
        val gradleJvmArgs = application.koin.getOrNull<List<String>>(named("gradleJvmArgs")) ?: emptyList()
        val dryRun = application.koin.getOrNull<Boolean>(named("dryRun")) ?: false

        // Run the tasks
        val result = taskRunnerService.runTasks(
            applicationDirectory = applicationDirectory,
            tasks = tasks,
            gradleArgs = gradleArgs,
            gradleJvmArgs = gradleJvmArgs,
            dryRun = dryRun,
            ciMetadata = ciMetadata
        )

        // Log the result
        taskRunnerService.logResult(result)
    }
}
