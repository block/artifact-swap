package xyz.block.artifactswap.cli.options

import java.nio.file.Path
import kotlin.io.path.Path
import picocli.CommandLine.Option

class BomPublishingOptions {

  @Option(names = ["--bom-version"], description = ["BOM version"])
  var bomVersion: String = ""
    internal set

  @Option(names = ["--hash-file-location"], description = ["Location of the hash file"])
  var hashPath: Path = Path(".")
    internal set
}
