package xyz.block.artifactswap.cli.options

import picocli.CommandLine.Option
import java.nio.file.Path
import kotlin.io.path.Path

class ArtifactRemoverOptions(
  mavenLocalPath: Path = Path(System.getProperty("user.home")).resolve(".m2")
) {

  @Option(
    names = ["--maven-local-path"],
    description = ["Local path to store downloaded artifacts"]
  )
  var mavenLocalPath: Path = mavenLocalPath
    internal set
}
