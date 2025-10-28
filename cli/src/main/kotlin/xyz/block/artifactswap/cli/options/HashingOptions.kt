package xyz.block.artifactswap.cli.options

import picocli.CommandLine.Option
import java.nio.file.Path

class HashingOptions {

    @Option(
        names = ["--use-build-logic-version"],
        description = ["Uses the build-logic version inside the gradle.properties"]
    )
    var useBuildLogicVersion: Boolean = false
        internal set

    @Option(
        names = ["--hashing-output-file"],
        description = ["Output for hashing file"]
    )
    var hashingFile: Path? = null
        internal set

    @Option(
        names = ["--bom-version"],
        description = ["BOM version to check artifactory for artifacts"]
    )
    var bomVersion: String = ""
        internal set

    @Option(
        names = ["--fine-grain-artifactory-check"],
        description = ["Does a check on artifactory for each hash result"]
    )
    var fineGrainedArtifactoryCheck: Boolean = false
        internal set

    @Option(
        names = ["--use-projects"],
        description = ["Project paths to use. All other projects are ignored."],
        split = " "
    )
    var useProjects: List<String> = emptyList()
        internal set
}
