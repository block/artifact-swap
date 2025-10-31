package xyz.block.artifactswap.cli.commands

import org.koin.core.KoinApplication
import picocli.CommandLine.Command
import picocli.CommandLine.ScopeType.INHERIT
import xyz.block.artifactswap.cli.AbstractArtifactSwapCommand
import xyz.block.artifactswap.cli.utils.VersionProvider

@Command(
    name = "sandbagging-tool",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class,
    description = ["Performs the requested sandbagging task"],
    scope = INHERIT
)
internal class SandbaggingCommand : AbstractArtifactSwapCommand() {

    override fun init(application: KoinApplication) {
        // does nothing for now
    }

    override suspend fun executeCommand(application: KoinApplication) {
        error("Sandbagging tool requires sub-command")
    }
}