package xyz.block.artifactswap.cli.options

import java.nio.file.Path
import kotlin.io.path.Path
import picocli.CommandLine.Option
import xyz.block.artifactswap.core.repository.DEFAULT_LOCAL_MAVEN_DIRECTORY

class ArtifactRemoverOptions {

  @Option(
    names = ["--maven-local-path"],
    description = ["Local path to store downloaded artifacts"],
  )
  var mavenLocalPath: Path = DEFAULT_LOCAL_MAVEN_DIRECTORY
    internal set
}
