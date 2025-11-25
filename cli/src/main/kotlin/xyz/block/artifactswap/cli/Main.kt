package xyz.block.artifactswap.cli

import kotlin.system.exitProcess
import kotlin.time.measureTimedValue
import org.apache.logging.log4j.kotlin.logger
import picocli.CommandLine
import xyz.block.artifactswap.cli.commands.ArtifactCheckerCommand
import xyz.block.artifactswap.cli.commands.ArtifactDownloaderCommand
import xyz.block.artifactswap.cli.commands.ArtifactRemoverCommand
import xyz.block.artifactswap.cli.commands.ArtifactSwapBaseCommand
import xyz.block.artifactswap.cli.commands.BomPublishingCommand
import xyz.block.artifactswap.cli.commands.HashingCommand
import xyz.block.artifactswap.cli.commands.TaskFinderCommand
import xyz.block.artifactswap.cli.commands.TaskRunnerCommand

// TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main(vararg args: String) {
  val (exitCode, duration) =
    measureTimedValue {
      val commandLine =
        CommandLine(ArtifactSwapBaseCommand())
          .addSubcommand(HashingCommand())
          .addSubcommand(TaskFinderCommand())
          .addSubcommand(TaskRunnerCommand())
          .addSubcommand(BomPublishingCommand())
          .addSubcommand(ArtifactCheckerCommand())
          .addSubcommand(ArtifactDownloaderCommand())
          .addSubcommand(ArtifactRemoverCommand())
      commandLine.isCaseInsensitiveEnumValuesAllowed = true
      val result = commandLine.execute(*args)
      commandLine.checkExitCode(result)
      return@measureTimedValue result
    }
  // Logger is set up in the ConfigurationsModule, which is done after the commandline has finished
  logger("xyz.block.artifactswap.cli.Main").debug { "Process ran for $duration" }
  exitProcess(exitCode)
}

// Does a quick exit code check, ensuring we don't hit any other code.
private fun CommandLine.checkExitCode(result: Int) {
  if (result == commandSpec.exitCodeOnInvalidInput()) {
    exitProcess(result)
  }

  if (isVersionHelpRequested || subcommands.any { (_, cm) -> cm.isVersionHelpRequested }) {
    exitProcess(commandSpec.exitCodeOnVersionHelp())
  }

  if (isUsageHelpRequested || subcommands.any { (_, cm) -> cm.isUsageHelpRequested }) {
    exitProcess(commandSpec.exitCodeOnUsageHelp())
  }
}
