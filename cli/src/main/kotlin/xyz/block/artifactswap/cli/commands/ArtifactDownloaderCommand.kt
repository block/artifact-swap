package xyz.block.artifactswap.cli.commands

import org.apache.logging.log4j.kotlin.logger
import org.koin.core.KoinApplication
import picocli.CommandLine
import picocli.CommandLine.Mixin
import xyz.block.artifactswap.cli.AbstractArtifactSwapCommand
import xyz.block.artifactswap.cli.options.ArtifactDownloaderOptions
import xyz.block.artifactswap.core.download.ArtifactDownloader
import xyz.block.artifactswap.core.download.di.ArtifactDownloaderConfig
import xyz.block.artifactswap.core.download.di.artifactDownloader
import xyz.block.artifactswap.core.download.di.artifactDownloaderModules

@CommandLine.Command(
    name = "download-artifacts",
    description = ["Downloads and stores Maven dependencies based on a given BOM file"]
)
class ArtifactDownloaderCommand : AbstractArtifactSwapCommand() {

    @Mixin
    private val artifactDownloaderOptions: ArtifactDownloaderOptions = ArtifactDownloaderOptions()
    private lateinit var downloader: ArtifactDownloader

    override fun init(application: KoinApplication) {
        val config = ArtifactDownloaderConfig(
            bomVersion = artifactDownloaderOptions.bomVersion,
            gradlePropertiesFile = artifactDownloaderOptions.gradlePropertiesFile,
            settingsGradleFile = artifactDownloaderOptions.settingsGradleFile,
            mavenLocalPath = artifactDownloaderOptions.mavenLocalPath,
        )
        application.modules(artifactDownloaderModules(application, config))
    }

    override suspend fun executeCommand(application: KoinApplication) {
        downloader = application.artifactDownloader

        logger.info { "Starting artifact downloader" }

        // Execute the download and installation process
        val result = downloader.downloadAndInstallArtifacts(
            bomVersion = artifactDownloaderOptions.bomVersion,
            settingsGradleFile = artifactDownloaderOptions.settingsGradleFile
        )

        logger.info { "Artifact downloader completed with result: ${result.result}" }
    }
}