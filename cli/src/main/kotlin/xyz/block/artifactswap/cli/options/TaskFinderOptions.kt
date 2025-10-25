package xyz.block.artifactswap.cli.options

import picocli.CommandLine.Option
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Output mode for task list.
 */
enum class OutputMode {
    /**
     * Split tasks into multiple files based on chunks and pages.
     */
    SHARD_TASK_LIST,

    /**
     * Output all tasks to a single file.
     */
    SINGLE_TASK_LIST
}

/**
 * CLI options for the task-finder command.
 */
class TaskFinderOptions {

    @Option(
        names = ["--task"],
        description = ["The task to find for all projects"],
        defaultValue = "publishToMavenLocal"
    )
    var task: String = ""
        internal set

    @Option(
        names = ["--task-list-output-directory"],
        description = ["Output directory for the task list"]
    )
    var taskListOutputDirectory: Path = Path("./taskOutputs")
        internal set

    @Option(
        names = ["--pages"],
        description = [
            "Number of files to split the task list output",
            "Each file will be appended by the number. Ex: task-output-1.out, task-output-2.out, ..."
        ],
        defaultValue = "1"
    )
    var pages: Int = 1
        internal set

    @Option(
        names = ["--chunks"],
        description = ["Number of tasks per page. Last page may have more or less than this number."],
        defaultValue = "1000"
    )
    var chunks: Int = 1000
        internal set

    @Option(
        names = ["--output-mode"],
        description = [
            "Output mode for task list",
            "SHARD_TASK_LIST: Split tasks into multiple files based on chunks and pages",
            "SINGLE_TASK_LIST: Output all tasks to a single file"
        ],
        defaultValue = "SHARD_TASK_LIST"
    )
    var outputMode: OutputMode = OutputMode.SHARD_TASK_LIST
        internal set
}
