package xyz.block.artifactswap.core.artifact_checker

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.launch
import org.apache.logging.log4j.kotlin.logger
import xyz.block.artifactswap.core.artifact_checker.models.ArtifactCheckerResult
import xyz.block.artifactswap.core.artifact_checker.models.ArtifactCheckerServiceResult
import xyz.block.artifactswap.core.artifact_checker.services.ArtifactCheckerEventStream
import xyz.block.artifactswap.core.network.ArtifactoryService
import java.io.FileNotFoundException
import java.time.Clock
import kotlin.time.measureTimedValue

/**
 * Service for checking if artifacts exist in Artifactory.
 */
class ArtifactCheckerService(
    private val artifactoryService: ArtifactoryService,
    private val eventStream: ArtifactCheckerEventStream,
    private val ioDispatcher: CoroutineDispatcher
) {
    /**
     * Checks if artifacts exist in Artifactory for the given project/task/version combinations.
     *
     * @param projectTaskVersions List of triples containing (projectPath, taskName, version)
     * @param ciMetadata CI metadata for analytics
     * @return Result containing list of missing artifacts and analytics data
     */
    suspend fun checkArtifacts(
        projectTaskVersions: List<Triple<String, String, String>>,
        ciMetadata: CiMetadata
    ): ArtifactCheckerServiceResult {
        val startTime = Clock.systemUTC().millis()
        var result = ArtifactCheckerServiceResult(
            countProjectsToCheck = projectTaskVersions.size,
            gitBranch = ciMetadata.gitBranch,
            gitSha = ciMetadata.gitSha,
            ciEnv = ciMetadata.ciEnv,
            buildId = ciMetadata.buildId,
            buildStepId = ciMetadata.buildStepId,
            buildJobId = ciMetadata.buildJobId,
            ciType = ciMetadata.ciType
        )

        // Check each artifact in Artifactory
        val (artifactsNotUploaded, checkArtifactoryDuration) = measureTimedValue {
            channelFlow {
                projectTaskVersions.forEach { (path, task, version) ->
                    launch {
                        try {
                            artifactoryService.artifactFilesExist(path.projectToArtifact(), version)
                        } catch (e: FileNotFoundException) {
                            logger.warn { e.message }
                            send("$path:$task")
                        } catch (e: Exception) {
                            logger.error(e) { "Error checking artifact for $path:$task" }
                            send("$path:$task")
                        }
                    }
                }
            }.toSet()
                .sorted()
        }

        result = result.copy(
            countArtifactsFound = projectTaskVersions.size - artifactsNotUploaded.size,
            checkArtifactoryDurationMs = checkArtifactoryDuration.inWholeMilliseconds,
            missingArtifacts = artifactsNotUploaded,
            result = ArtifactCheckerResult.SUCCESS,
            totalDurationMs = Clock.systemUTC().millis() - startTime
        )

        return result
    }

    /**
     * Logs the result to the event stream.
     */
    suspend fun logResult(result: ArtifactCheckerServiceResult) {
        eventStream.sendResults(listOf(result))
    }

    /**
     * Converts a project path to an artifact name.
     * Removes the leading colon and replaces remaining colons with underscores.
     */
    private fun String.projectToArtifact(): String {
        return drop(1).replace(':', '_')
    }
}

/**
 * CI metadata for analytics.
 */
data class CiMetadata(
    val gitBranch: String = System.getenv("GIT_BRANCH").orEmpty(),
    val gitSha: String = System.getenv("GIT_COMMIT").orEmpty(),
    val ciEnv: String = System.getenv("KOCHIKU_ENV").orEmpty(),
    val buildId: String = "",
    val buildStepId: String = "",
    val buildJobId: String = "",
    val ciType: String = ""
)
