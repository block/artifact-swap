package xyz.block.artifactswap.cli.commands

import org.koin.core.KoinApplication
import picocli.CommandLine.Command
import picocli.CommandLine.ScopeType.INHERIT
import xyz.block.artifactswap.cli.AbstractArtifactSwapCommand
import xyz.block.artifactswap.cli.utils.VersionProvider

@Command(
  name = "artifactswap-cli",
  mixinStandardHelpOptions = true,
  versionProvider = VersionProvider::class,
  description = ["Performs the requested artifact swap task"],
  scope = INHERIT,
)
internal class ArtifactSwapBaseCommand : AbstractArtifactSwapCommand() {

  override fun init(application: KoinApplication) {
    // does nothing for now
  }

  override suspend fun executeCommand(application: KoinApplication): Int {
    error("Artifact Swap CLI requires sub-command")
  }
}
