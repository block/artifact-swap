package xyz.block.artifactswap.cli.options

import java.nio.file.Path
import kotlin.io.path.Path
import picocli.CommandLine.Option

/** CLI options for the task-runner command. */
class TaskRunnerOptions {

  @Option(names = ["--task-list-file"], description = ["File containing list of tasks to run"])
  var taskRunFile: Path = Path(".")
    internal set
}
