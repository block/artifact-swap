package xyz.block.artifactswap.cli.utils

import org.apache.logging.log4j.Level
import picocli.CommandLine.ITypeConverter

// Converts string to logback level. For Picocli.
internal class LogBackLogLevelTypeConverter : ITypeConverter<Level> {

    override fun convert(value: String?): Level {
        return Level.toLevel(value)
    }
}
