package xyz.block.artifactswap.cli.options

import java.nio.file.Path
import kotlin.io.path.Path
import picocli.CommandLine.Option

class ArtifactRemoverOptions(
  mavenLocalPath: Path = Path(System.getProperty("user.home")).resolve(".m2")
) {

  @Option(
    names = ["--maven-local-path"],
    description = ["Local path to store downloaded artifacts"],
  )
  var mavenLocalPath: Path = mavenLocalPath
    internal set
}
