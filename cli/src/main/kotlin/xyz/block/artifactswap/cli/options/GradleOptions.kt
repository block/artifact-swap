package xyz.block.artifactswap.cli.options

import picocli.CommandLine.Option

/**
 * Basic options for all Gradle based commands
 */
internal class GradleOptions {

    @Option(
        names = ["--log-gradle"],
        description = ["Output Gradle logs"]
    )
    var logGradle: Boolean = false
        internal set

    @Option(
        names = ["--gradle-args"],
        description = ["Arguments to pass to Gradle daemon"],
        split = " "
    )
    var gradleArgs: List<String> = emptyList()
        internal set

    @Option(
        names = ["--gradle-jvm-args"],
        description = ["Arguments to pass for the Gradle Java VM"],
        split = " "
    )
    var gradleJvmArgs: List<String> = emptyList()

    @Option(
        names = ["--gradle-max-memory"],
        description = [
            "Sets the max JVM memory size for Gradle daemon (in MB)",
            "Equivalent to using '-Xmx' for the Gradle JVM args"
        ]
    )
    var maxGradleMemory: Int? = null
        internal set
}
