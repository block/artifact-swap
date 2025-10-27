package xyz.block.artifactswap.core.publisher

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.apache.logging.log4j.kotlin.logger
import retrofit2.Response
import xyz.block.artifactswap.core.maven.Dependencies
import xyz.block.artifactswap.core.maven.Dependency
import xyz.block.artifactswap.core.maven.DependencyManagement
import xyz.block.artifactswap.core.maven.Metadata
import xyz.block.artifactswap.core.maven.Project
import xyz.block.artifactswap.core.maven.Versioning
import xyz.block.artifactswap.core.maven.Versions
import xyz.block.artifactswap.core.network.ArtifactoryEndpoints
import xyz.block.artifactswap.core.publisher.models.BomPublisherResult
import xyz.block.artifactswap.core.publisher.models.BomPublishingResult
import xyz.block.artifactswap.core.publisher.services.BomPublisherEventStream
import xyz.block.artifactswap.core.publisher.services.ProjectHashReader
import java.net.HttpURLConnection
import java.nio.file.Path
import java.time.Clock
import kotlin.time.measureTimedValue

/**
 * Service for publishing BOM (Bill of Materials) to Artifactory.
 */
class BomPublisher(
    private val projectHashReader: ProjectHashReader,
    private val artifactoryEndpoints: ArtifactoryEndpoints,
    private val eventStream: BomPublisherEventStream,
    private val dryRun: Boolean = false
) {
    companion object {
        private const val GROUP_ID = "com.squareup.register.sandbags"
        private const val BOM = "bom"
        private const val REPO = "android-register-sandbags"
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
        ciMetadata: CiMetadata
    ): BomPublisherResult = coroutineScope {
        val startTime = Clock.systemUTC().millis()
        var result = BomPublisherResult(
            gitBranch = ciMetadata.gitBranch,
            gitSha = ciMetadata.gitSha,
            kochikuEnv = ciMetadata.kochikuEnv,
            buildId = ciMetadata.buildId,
            buildStepId = ciMetadata.buildStepId,
            buildJobId = ciMetadata.buildJobId,
            ciType = ciMetadata.ciType
        )

        logger.info { "Reading hash output from $hashPath" }
        // Get the artifact-version dictionary
        val (projectHashMap, readProjectHashMapDuration) = measureTimedValue {
            projectHashReader.readProjectHashes(hashPath)
        }

        if (projectHashMap.isFailure) {
            logger.error { "Failed to read project hashes: ${projectHashMap.exceptionOrNull()}" }
            return@coroutineScope result.copy(
                result = BomPublishingResult.FAILED_READING_PROJECT_HASHES
            )
        }

        val projectHashes = projectHashMap.getOrThrow()
        result = result.copy(
            readHashedProjectsDurationMs = readProjectHashMapDuration.inWholeMilliseconds,
            countProjectsHashed = projectHashes.size.toLong()
        )

        // If no project hashes, nothing to publish
        if (projectHashes.isEmpty()) {
            logger.info { "No project hashes found, nothing to publish" }
            return@coroutineScope result.copy(
                result = BomPublishingResult.FAILED_FETCHING_PUBLISHED_PROJECT_DATA,
                totalDurationMs = Clock.systemUTC().millis() - startTime
            )
        }

        logger.info { "Collecting artifacts currently in artifactory" }
        // Convert all successful artifact uploads to Dependency objects
        val (dependencies, fetchArtifactoryDataDuration) = measureTimedValue {
            fetchPublishedDependencies(projectHashes)
        }

        result = result.copy(
            requestProjectDataArtifactoryDurationMs = fetchArtifactoryDataDuration.inWholeMilliseconds,
            countProjectsInArtifactory = dependencies.size.toLong()
        )

        logger.info { "Got ${dependencies.count()} dependencies for this BOM" }
        // Consider artifactory to be down if every fetch failed
        if (dependencies.isEmpty() && projectHashes.isNotEmpty()) {
            logger.info { "Not publishing updated BOM since none of requested projects were published to Artifactory." }
            return@coroutineScope result.copy(
                result = BomPublishingResult.FAILED_FETCHING_PUBLISHED_PROJECT_DATA,
                totalDurationMs = Clock.systemUTC().millis() - startTime
            )
        }

        // Prepare pom file for BOM
        val project = Project(
            groupId = GROUP_ID,
            artifactId = BOM,
            version = bomVersion,
            name = BOM,
            dependencyManagement = DependencyManagement(
                Dependencies(
                    dependency = dependencies
                )
            )
        )

        logger.info { "Pushing BOM artifact" }
        val publishPomStart = Clock.systemUTC().millis()
        val pomResponse = if (dryRun) {
            logger.info { "Dry run, not pushing BOM artifact" }
            Response.success(Unit)
        } else {
            artifactoryEndpoints.pushPom(REPO, BOM, bomVersion, "$BOM-$bomVersion.pom", project)
        }

        result = if (pomResponse.isSuccessful) {
            logger.info { "BOM artifact pushed!" }
            val updatedResult = result.copy(
                countProjectsIncludedInBom = dependencies.size.toLong()
            )
            updateBomMetadata(bomVersion, updatedResult)
        } else {
            logger.error {
                "Failed to push bom artifact (${pomResponse.code()}): " +
                    (pomResponse.errorBody()?.string() ?: pomResponse.message())
            }
            result.copy(
                result = BomPublishingResult.FAILED_PUBLISHING_UPDATED_POM
            )
        }

        result.copy(
            publishUpdatedBomAndMetadataDurationMs = Clock.systemUTC().millis() - publishPomStart,
            totalDurationMs = Clock.systemUTC().millis() - startTime
        )
    }

    /**
     * Logs the result to the event stream.
     */
    suspend fun logResult(result: BomPublisherResult) {
        eventStream.sendResults(listOf(result))
    }

    private suspend fun fetchPublishedDependencies(projectHashes: Map<String, String>): List<Dependency> = coroutineScope {
        return@coroutineScope projectHashes.map { (artifact, version) ->
            // Query the artifactory repository to see if the artifact exists
            async {
                val response = artifactoryEndpoints.getMavenMetadata(REPO, artifact)
                if (response.isSuccessful) {
                    val metadata = response.body()
                    if (metadata == null) {
                        logger.info { "Got OK, but metadata was null for $artifact" }
                        return@async null
                    }

                    // Artifact may exist, but it's possible the version hashed is not present or failed to publish
                    if (metadata.versioning.versions.version.asReversed().contains(version)) {
                        return@async Dependency(
                            groupId = metadata.groupId,
                            artifactId = metadata.artifactId,
                            version = version
                        )
                    }
                    logger.warn { "Artifact $artifact version not found in metadata" }
                    return@async null
                } else if (response.code() != HttpURLConnection.HTTP_NOT_FOUND) {
                    logger.error(
                        "Unable to get maven metadata for $artifact (${response.code()}): " +
                            (response.errorBody()?.string() ?: response.message())
                    )
                    return@async null
                }
                return@async null
            }
        }
            .awaitAll()
            .filterNotNull()
    }

    private suspend fun updateBomMetadata(newVersion: String, result: BomPublisherResult): BomPublisherResult {
        val bomMetaDataResponse = artifactoryEndpoints.getMavenMetadata(REPO, BOM)
        if (bomMetaDataResponse.isSuccessful) {
            // Create or update the BOM metadata
            val newBomMetaData = bomMetaDataResponse.body()?.let { bomMetaData ->
                logger.info { "Found existing bom metadata: $bomMetaData" }
                bomMetaData.copy(
                    versioning = bomMetaData.versioning.copy(
                        latest = newVersion,
                        release = newVersion,
                        versions = bomMetaData.versioning.versions.copy(
                            version = bomMetaData.versioning.versions.version + newVersion
                        )
                    )
                )
            } ?: Metadata(
                groupId = GROUP_ID,
                artifactId = BOM,
                versioning = Versioning(
                    latest = newVersion,
                    release = newVersion,
                    versions = Versions(listOf(newVersion)),
                    lastUpdated = Clock.systemUTC().millis()
                )
            )

            // Avoid extra API calls
            if (newBomMetaData != bomMetaDataResponse.body()) {
                logger.info { "Updating existing bom metadata: $newBomMetaData" }
                val response = if (dryRun) {
                    logger.info { "Dry run, not pushing BOM metadata" }
                    Response.success(Unit)
                } else {
                    artifactoryEndpoints.pushMetadata(
                        repo = REPO,
                        artifact = BOM,
                        metadata = newBomMetaData
                    )
                }
                return if (!response.isSuccessful) {
                    logger.error(
                        "Pushing metadata update failed (${response.code()}): " +
                            (response.errorBody()?.string() ?: response.message())
                    )
                    result.copy(result = BomPublishingResult.SUCCESS_BOM_PUBLISHED_METADATA_FAILED)
                } else {
                    result.copy(result = BomPublishingResult.SUCCESS_BOM_AND_METADATA_PUBLISHED)
                }
            } else {
                return result.copy(result = BomPublishingResult.SUCCESS_BOM_PUBLISHED_METADATA_NO_UPDATE)
            }
        } else if (bomMetaDataResponse.code() == HttpURLConnection.HTTP_NOT_FOUND) {
            val response = artifactoryEndpoints.pushMetadata(
                repo = REPO,
                artifact = BOM,
                metadata = Metadata(
                    groupId = GROUP_ID,
                    artifactId = BOM,
                    versioning = Versioning(
                        latest = newVersion,
                        release = newVersion,
                        versions = Versions(listOf(newVersion)),
                        lastUpdated = Clock.systemUTC().millis()
                    )
                )
            )

            return if (!response.isSuccessful) {
                logger.error {
                    "Pushing missing metadata failed (${response.code()}): " +
                        (response.errorBody()?.string() ?: response.message())
                }
                result.copy(result = BomPublishingResult.SUCCESS_BOM_PUBLISHED_METADATA_FAILED)
            } else {
                result.copy(result = BomPublishingResult.SUCCESS_BOM_AND_METADATA_PUBLISHED)
            }
        }
        return result
    }
}

/**
 * CI metadata for analytics.
 */
data class CiMetadata(
    val gitBranch: String = System.getenv("GIT_BRANCH").orEmpty(),
    val gitSha: String = System.getenv("GIT_COMMIT").orEmpty(),
    val kochikuEnv: String = System.getenv("KOCHIKU_ENV").orEmpty(),
    val buildId: String = "",
    val buildStepId: String = "",
    val buildJobId: String = "",
    val ciType: String = ""
)
