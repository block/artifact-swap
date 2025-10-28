package xyz.block.artifactswap.cli.commands

import xyz.block.artifactswap.cli.AbstractArtifactSwapCommand
import xyz.block.artifactswap.cli.options.ArtifactRemoverOptions
import xyz.block.artifactswap.core.remover.ArtifactRemover
import xyz.block.artifactswap.core.remover.di.ArtifactRemoverConfig
import xyz.block.artifactswap.core.remover.di.artifactRemover
import xyz.block.artifactswap.core.remover.di.artifactRemoverModules
import org.koin.core.KoinApplication
import picocli.CommandLine
import picocli.CommandLine.Mixin

@CommandLine.Command(
  name = "artifact-remover",
  description = ["Examines local m2 repository and removes artifacts that are not needed"]
)
class ArtifactRemoverCommand : AbstractArtifactSwapCommand() {

  @Mixin
  private val artifactRemoverOptions: ArtifactRemoverOptions = ArtifactRemoverOptions()
  private lateinit var artifactRemover: ArtifactRemover

  override fun init(application: KoinApplication) {
    val config = ArtifactRemoverConfig(
      mavenLocalPath = artifactRemoverOptions.mavenLocalPath
    )
    application.modules(artifactRemoverModules(application, config))
  }

  override suspend fun executeCommand(application: KoinApplication) {
    artifactRemover = application.artifactRemover
    val result = artifactRemover.removeArtifacts()
    artifactRemover.logResult(result)
  }
}
