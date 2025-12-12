package xyz.block.artifactswap.core.publisher

import java.nio.file.Path
import java.time.Clock
import kotlin.time.measureTimedValue
import kotlinx.coroutines.coroutineScope
import org.apache.logging.log4j.kotlin.logger
import xyz.block.artifactswap.core.config.ArtifactSwapConfig
import xyz.block.artifactswap.core.maven.Dependencies
import xyz.block.artifactswap.core.maven.DependencyManagement
import xyz.block.artifactswap.core.maven.Metadata
import xyz.block.artifactswap.core.maven.Project
import xyz.block.artifactswap.core.maven.Versioning
import xyz.block.artifactswap.core.maven.Versions
import xyz.block.artifactswap.core.publisher.models.BomPublisherResult
import xyz.block.artifactswap.core.publisher.models.BomPublishingResult
import xyz.block.artifactswap.core.publisher.services.BomPublisherEventStream
import xyz.block.artifactswap.core.publisher.services.ProjectHashReader

/** Service for publishing BOM (Bill of Materials) to a repository. */
class BomPublisher(
  private val projectHashReader: ProjectHashReader,
  private val bomRepository: BomRepository,
  private val eventStream: BomPublisherEventStream,
  private val config: ArtifactSwapConfig,
  private val dryRun: Boolean = false,
) {
  companion object {
    private const val BOM = "bom"
    private val logger = logger("BomPublisher")
  }

  /**
   * Publishes a BOM based on project hashes from the given file.
   *
   * @param bomVersion The version to use for the BOM
   * @param hashPath Path to the file containing project hash mappings
   * @param ciMetadata CI metadata for analytics
   * @return Result of the publishing operation
   */
  suspend fun publishBom(
    bomVersion: String,
    hashPath: Path,
    ciMetadata: CiMetadata,
  ): BomPublisherResult = coroutineScope {
    val startTime = Clock.systemUTC().millis()
    var result =
      BomPublisherResult(
        gitBranch = ciMetadata.gitBranch,
        gitSha = ciMetadata.gitSha,
        kochikuEnv = ciMetadata.kochikuEnv,
        buildId = ciMetadata.buildId,
        buildStepId = ciMetadata.buildStepId,
        buildJobId = ciMetadata.buildJobId,
        ciType = ciMetadata.ciType,
      )

    logger.info { "Reading hash output from $hashPath" }
    // Get the artifact-version dictionary
    val (projectHashMap, readProjectHashMapDuration) =
      measureTimedValue { projectHashReader.readProjectHashes(hashPath) }

    if (projectHashMap.isFailure) {
      logger.error { "Failed to read project hashes: ${projectHashMap.exceptionOrNull()}" }
      return@coroutineScope result.copy(result = BomPublishingResult.FAILED_READING_PROJECT_HASHES)
    }

    val projectHashes = projectHashMap.getOrThrow()
    result =
      result.copy(
        readHashedProjectsDurationMs = readProjectHashMapDuration.inWholeMilliseconds,
        countProjectsHashed = projectHashes.size.toLong(),
      )

    // If no project hashes, nothing to publish
    if (projectHashes.isEmpty()) {
      logger.info { "No project hashes found, nothing to publish" }
      return@coroutineScope result.copy(
        result = BomPublishingResult.FAILED_FETCHING_PUBLISHED_PROJECT_DATA,
        totalDurationMs = Clock.systemUTC().millis() - startTime,
      )
    }

    logger.info { "Collecting available artifacts from repository" }
    // Convert all successful artifact uploads to Dependency objects
    val (dependencies, fetchArtifactoryDataDuration) =
      measureTimedValue { bomRepository.fetchAvailableDependencies(projectHashes) }

    result =
      result.copy(
        requestProjectDataArtifactoryDurationMs = fetchArtifactoryDataDuration.inWholeMilliseconds,
        countProjectsInArtifactory = dependencies.size.toLong(),
      )

    logger.info { "Got ${dependencies.count()} dependencies for this BOM" }
    // Consider repository to be down if every fetch failed
    if (dependencies.isEmpty() && projectHashes.isNotEmpty()) {
      logger.info {
        "Not publishing updated BOM since none of requested projects were available in repository."
      }
      return@coroutineScope result.copy(
        result = BomPublishingResult.FAILED_FETCHING_PUBLISHED_PROJECT_DATA,
        totalDurationMs = Clock.systemUTC().millis() - startTime,
      )
    }

    // Prepare pom file for BOM
    val project =
      Project(
        groupId = config.primaryArtifactsMavenGroup,
        artifactId = BOM,
        version = bomVersion,
        name = BOM,
        dependencyManagement = DependencyManagement(Dependencies(dependency = dependencies)),
      )

    logger.info { "Publishing BOM artifact" }
    val publishPomStart = Clock.systemUTC().millis()

    result =
      if (dryRun) {
        logger.info { "Dry run, not pushing BOM artifact" }
        result.copy(
          countProjectsIncludedInBom = dependencies.size.toLong(),
          result = BomPublishingResult.SUCCESS_BOM_AND_METADATA_PUBLISHED,
        )
      } else {
        val publishResult = bomRepository.publishBom(project, bomVersion)
        if (publishResult.isSuccess) {
          logger.info { "BOM artifact published!" }
          val updatedResult = result.copy(countProjectsIncludedInBom = dependencies.size.toLong())
          updateBomMetadata(bomVersion, updatedResult)
        } else {
          logger.error {
            "Failed to publish BOM artifact: ${publishResult.exceptionOrNull()?.message}"
          }
          result.copy(result = BomPublishingResult.FAILED_PUBLISHING_UPDATED_POM)
        }
      }

    result.copy(
      publishUpdatedBomAndMetadataDurationMs = Clock.systemUTC().millis() - publishPomStart,
      totalDurationMs = Clock.systemUTC().millis() - startTime,
    )
  }

  /** Logs the result to the event stream. */
  suspend fun logResult(result: BomPublisherResult) {
    eventStream.sendResults(listOf(result))
  }

  private suspend fun updateBomMetadata(
    newVersion: String,
    result: BomPublisherResult,
  ): BomPublisherResult {
    // Fetch existing metadata
    val metadataResult = bomRepository.fetchBomMetadata(BOM)
    if (metadataResult.isFailure) {
      logger.error { "Failed to fetch BOM metadata: ${metadataResult.exceptionOrNull()?.message}" }
      return result.copy(result = BomPublishingResult.SUCCESS_BOM_PUBLISHED_METADATA_FAILED)
    }

    val existingMetadata = metadataResult.getOrNull()

    // Create or update the BOM metadata
    val newMetadata =
      existingMetadata?.let { bomMetaData ->
        logger.info { "Found existing BOM metadata: $bomMetaData" }
        bomMetaData.copy(
          versioning =
            bomMetaData.versioning.copy(
              latest = newVersion,
              release = newVersion,
              versions =
                if (bomMetaData.versioning.versions.version.contains(newVersion)) {
                  bomMetaData.versioning.versions
                } else {
                  bomMetaData.versioning.versions.copy(
                    version = bomMetaData.versioning.versions.version + newVersion
                  )
                },
              lastUpdated = Clock.systemUTC().millis(),
            )
        )
      }
        ?: Metadata(
          groupId = config.primaryArtifactsMavenGroup,
          artifactId = BOM,
          versioning =
            Versioning(
              latest = newVersion,
              release = newVersion,
              versions = Versions(listOf(newVersion)),
              lastUpdated = Clock.systemUTC().millis(),
            ),
        )

    // Publish metadata if it changed
    if (newMetadata != existingMetadata) {
      logger.info { "Publishing BOM metadata" }
      val publishResult = bomRepository.publishBomMetadata(newMetadata)
      return if (publishResult.isSuccess) {
        result.copy(result = BomPublishingResult.SUCCESS_BOM_AND_METADATA_PUBLISHED)
      } else {
        logger.error {
          "Failed to publish BOM metadata: ${publishResult.exceptionOrNull()?.message}"
        }
        result.copy(result = BomPublishingResult.SUCCESS_BOM_PUBLISHED_METADATA_FAILED)
      }
    } else {
      return result.copy(result = BomPublishingResult.SUCCESS_BOM_PUBLISHED_METADATA_NO_UPDATE)
    }
  }
}

/** CI metadata for analytics. */
data class CiMetadata(
  val gitBranch: String = System.getenv("GIT_BRANCH").orEmpty(),
  val gitSha: String = System.getenv("GIT_COMMIT").orEmpty(),
  val kochikuEnv: String = System.getenv("KOCHIKU_ENV").orEmpty(),
  val buildId: String = "",
  val buildStepId: String = "",
  val buildJobId: String = "",
  val ciType: String = "",
)
