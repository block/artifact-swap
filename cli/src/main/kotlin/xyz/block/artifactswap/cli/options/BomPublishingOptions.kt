package xyz.block.artifactswap.cli.options

import picocli.CommandLine.Option
import java.nio.file.Path
import kotlin.io.path.Path

class BomPublishingOptions {

    @Option(
        names = ["--bom-version"],
        description = ["BOM version"]
    )
    var bomVersion: String = ""
        internal set

    @Option(
        names = ["--hash-file-location"],
        description = ["Location of the hash file"]
    )
    var hashPath: Path = Path(".")
        internal set
}
