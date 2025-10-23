package xyz.block.artifactswap.cli.options

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
import picocli.CommandLine.Option
import xyz.block.artifactswap.cli.network.EventStreamLoggingEnvironment
import xyz.block.artifactswap.cli.utils.LogBackLogLevelTypeConverter
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory

/**
 * Common options for all commands
 */
internal class CommonOptions {

    @Option(
        names = ["--dir"],
        description = ["Project directory"],
        defaultValue = "."
    )
    var directory: Path = Path(".").toRealPath()
        internal set(value) {
            require(value.isDirectory()) { "Param --dir must point to a valid directory" }
            field = value.toRealPath()
        }


    @set:Option(
        names = ["--logging_environment"],
        description = ["Logging Environment (defaults to staging)"],
        defaultValue = "PRODUCTION"
    )
    lateinit var loggingEnvironment: EventStreamLoggingEnvironment
        internal set

    @Option(
        names = ["--dry-run"],
        description = ["Dry run mode doesn't publish artifacts to artifactory"]
    )
    var dryRun: Boolean = false
        internal set

    @Option(
        names = ["--logging"],
        description = ["Logging level (OFF, FATAL, ERROR, WARN, INFO, DEBUG, TRACE, ALL)"],
        converter = [LogBackLogLevelTypeConverter::class],
        defaultValue = "info"
    )
    private fun setLogLevel(level: Level) {
        Configurator.setRootLevel(level)
    }
}
