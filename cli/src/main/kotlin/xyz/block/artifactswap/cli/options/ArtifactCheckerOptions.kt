package xyz.block.artifactswap.cli.options

import picocli.CommandLine.Option
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * CLI options for the artifact-checker command.
 */
class ArtifactCheckerOptions {

    @Option(
        names = ["--hash-file"],
        description = ["Hash file containing project paths and their hashes"]
    )
    var hashFile: Path = Path(".")
        internal set

    @Option(
        names = ["--input-file"],
        description = ["Input file containing project paths with tasks to check for in artifactory"]
    )
    var inputFile: Path = Path(".")
        internal set

    @Option(
        names = ["--output-file"],
        description = [
            "File containing the list of project paths with tasks that don't exist in artifactory"
        ],
    )
    var outputFile: Path = Path(".")
        internal set
}
