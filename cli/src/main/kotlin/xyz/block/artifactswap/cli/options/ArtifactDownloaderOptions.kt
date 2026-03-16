package xyz.block.artifactswap.cli.options

import picocli.CommandLine.Option

class ArtifactDownloaderOptions(bomVersion: String = "") {

  @Option(
    names = ["--bom-version"],
    description = ["BOM version to check artifactory for artifacts"],
  )
  var bomVersion: String = bomVersion
    internal set
}
