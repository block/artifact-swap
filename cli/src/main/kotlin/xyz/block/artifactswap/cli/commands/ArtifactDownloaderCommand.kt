package xyz.block.artifactswap.cli.commands

import org.apache.logging.log4j.kotlin.logger
import org.koin.core.KoinApplication
import picocli.CommandLine
import picocli.CommandLine.Mixin
import xyz.block.artifactswap.cli.AbstractArtifactSwapCommand
import xyz.block.artifactswap.cli.di.ArtifactDownloaderConfig
import xyz.block.artifactswap.cli.di.artifactDownloader
import xyz.block.artifactswap.cli.di.artifactDownloaderModules
import xyz.block.artifactswap.cli.options.ArtifactDownloaderOptions
import xyz.block.artifactswap.core.download.ArtifactDownloader

@CommandLine.Command(
  name = "download-artifacts",
  description = ["Downloads and stores Maven dependencies based on a given BOM file"],
)
class ArtifactDownloaderCommand : AbstractArtifactSwapCommand() {

  @Mixin
  private val artifactDownloaderOptions: ArtifactDownloaderOptions = ArtifactDownloaderOptions()
  private lateinit var downloader: ArtifactDownloader

  override fun init(application: KoinApplication) {
    val config =
      ArtifactDownloaderConfig(
        bomVersion = artifactDownloaderOptions.bomVersion,
        settingsGradleFile = artifactDownloaderOptions.settingsGradleFile,
      )
    application.modules(artifactDownloaderModules(application, config))
  }

  override suspend fun executeCommand(application: KoinApplication): Int {
    downloader = application.artifactDownloader

    logger.info { "Starting artifact downloader" }

    // Execute the download and installation process
    val result =
      downloader.downloadAndInstallArtifacts(
        bomVersion = artifactDownloaderOptions.bomVersion,
        settingsGradleFile = artifactDownloaderOptions.settingsGradleFile,
      )

    logger.info { "Artifact downloader completed with result: ${result.result}" }
    return result.result.exitCode
  }
}
