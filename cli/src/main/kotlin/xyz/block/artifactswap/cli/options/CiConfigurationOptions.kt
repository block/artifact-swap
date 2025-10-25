package xyz.block.artifactswap.cli.options

import picocli.CommandLine.Option

/**
 * CI specific configurations for analytics
 */
class CiConfigurationOptions {

    @set:Option(
        names = ["--build_id"],
        description = ["Build id for this analysis."]
    )
    var buildId: String = ""

    @set:Option(
        names = ["--build_step_id"],
        description = ["Build step id for this analysis."]
    )
    var buildStepId: String = ""
        internal set

    @set:Option(
        names = ["--build_job_id"],
        description = ["Build attempt id for this analysis."]
    )
    var buildJobId: String = ""
        internal set

    @set:Option(
        names = ["--ci_type"],
        description = ["CI Type (kochiku or buildkite)"]
    )
    var ciType: String = ""
        internal set
}
