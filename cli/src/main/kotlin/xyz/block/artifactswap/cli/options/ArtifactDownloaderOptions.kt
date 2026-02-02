package xyz.block.artifactswap.cli.options

import java.nio.file.Path
import picocli.CommandLine.Option

class ArtifactDownloaderOptions(bomVersion: String = "", settingsGradleFile: Path? = null) {

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
}
