package xyz.block.artifactswap.cli.commands

import org.apache.logging.log4j.kotlin.logger
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import picocli.CommandLine
import picocli.CommandLine.Mixin
import xyz.block.artifactswap.cli.AbstractArtifactSwapCommand
import xyz.block.artifactswap.cli.di.BomPublisherConfig
import xyz.block.artifactswap.cli.di.bomPublisher
import xyz.block.artifactswap.cli.di.bomPublisherModules
import xyz.block.artifactswap.cli.options.BomPublishingOptions
import xyz.block.artifactswap.cli.options.CiConfigurationOptions
import xyz.block.artifactswap.core.publisher.BomPublisher
import xyz.block.artifactswap.core.publisher.CiMetadata

@CommandLine.Command(
  name = "bom-publisher",
  description = ["Publishes the BOM for the given list of projects."],
)
class BomPublishingCommand : AbstractArtifactSwapCommand() {

  @Mixin private lateinit var bomPublishingOptions: BomPublishingOptions

  @Mixin private lateinit var ciConfigurationOptions: CiConfigurationOptions

  private lateinit var bomPublisher: BomPublisher

  override fun init(application: KoinApplication) {
    val config =
      BomPublisherConfig(
        dryRun = application.koin.get(named("dryRun")),
        local = bomPublishingOptions.local,
      )
    application.modules(bomPublisherModules(application, config))
  }

  override suspend fun executeCommand(application: KoinApplication): Int {
    bomPublisher = application.bomPublisher

    logger.info { "Starting BOM publisher" }

    val ciMetadata =
      CiMetadata(
        buildId = ciConfigurationOptions.buildId,
        buildStepId = ciConfigurationOptions.buildStepId,
        buildJobId = ciConfigurationOptions.buildJobId,
        ciType = ciConfigurationOptions.ciType,
      )

    val result =
      bomPublisher.publishBom(
        bomVersion = bomPublishingOptions.bomVersion,
        hashPath = bomPublishingOptions.hashPath,
        ciMetadata = ciMetadata,
      )

    logger.info { "BOM publisher completed with result: ${result.result}" }

    bomPublisher.logResult(result)
    return result.result.exitCode
  }
}
