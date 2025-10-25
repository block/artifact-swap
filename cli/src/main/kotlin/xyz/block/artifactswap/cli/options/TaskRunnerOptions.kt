package xyz.block.artifactswap.cli.options

import picocli.CommandLine.Option
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * CLI options for the task-runner command.
 */
class TaskRunnerOptions {

    @Option(
        names = ["--task-list-file"],
        description = ["File containing list of tasks to run"]
    )
    var taskRunFile: Path = Path(".")
        internal set
}
