package xyz.block.artifactswap.cli.options

import java.nio.file.Path
import kotlin.io.path.Path
import picocli.CommandLine.Option
import xyz.block.artifactswap.core.repository.DEFAULT_LOCAL_MAVEN_DIRECTORY

class ArtifactDownloaderOptions(
  bomVersion: String = "",
  settingsGradleFile: Path? = null,
  mavenLocalPath: Path = DEFAULT_LOCAL_MAVEN_DIRECTORY,
) {

  @Option(
    names = ["--bom-version"],
    description = ["BOM version to check artifactory for artifacts"],
  )
  var bomVersion: String = bomVersion
    internal set

  @Option(
    names = ["--settings-gradle-file"],
    description = ["(Optional) Path to settings.gradle to extract protos projects from"],
  )
  var settingsGradleFile: Path? = settingsGradleFile
    internal set

  @Option(
    names = ["--maven-local-path"],
    description = ["Local path to store downloaded artifacts"],
  )
  var mavenLocalPath: Path = mavenLocalPath
    internal set
}
