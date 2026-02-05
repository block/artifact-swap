package xyz.block.artifactswap.cli.commands

import org.koin.core.KoinApplication
import picocli.CommandLine
import xyz.block.artifactswap.cli.AbstractArtifactSwapCommand
import xyz.block.artifactswap.cli.di.artifactRemover
import xyz.block.artifactswap.cli.di.artifactRemoverModules
import xyz.block.artifactswap.core.remover.ArtifactRemover

@CommandLine.Command(
  name = "artifact-remover",
  description = ["Examines local m2 repository and removes artifacts that are not needed"],
)
class ArtifactRemoverCommand : AbstractArtifactSwapCommand() {

  private lateinit var artifactRemover: ArtifactRemover

  override fun init(application: KoinApplication) {
    application.modules(artifactRemoverModules())
  }

  override suspend fun executeCommand(application: KoinApplication): Int {
    artifactRemover = application.artifactRemover
    val result = artifactRemover.removeArtifacts()
    artifactRemover.logResult(result)
    return result.result.exitCode
  }
}
