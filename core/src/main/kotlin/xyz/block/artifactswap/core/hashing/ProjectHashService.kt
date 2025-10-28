package xyz.block.artifactswap.core.hashing

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.kotlin.logger
import xyz.block.artifactswap.core.gradle.GradleProjectsProvider
import xyz.block.artifactswap.core.gradle.ProjectHashingInfo
import xyz.block.artifactswap.core.hashing.models.HashingServiceResult
import xyz.block.artifactswap.core.hashing.models.ProjectHashingResult
import xyz.block.artifactswap.core.hashing.services.HashingEventStream
import java.nio.ByteBuffer
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.util.Properties
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.inputStream
import kotlin.time.measureTimedValue

/**
 * Service for hashing Gradle project source files.
 */
class ProjectHashService(
    private val gradleProjectsProvider: GradleProjectsProvider,
    private val eventStream: HashingEventStream,
    private val ioDispatcher: CoroutineContext,
    private val defaultDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val HASHING_SEED = 0L
        private const val GRADLE_PROPERTIES_FILE_NAME = "gradle.properties"
        private const val BUILD_LOGIC_CONSUMER_KEY = "square.registerPluginsVersion.consumer"
        private const val SHA_256 = "SHA-256"
    }

    private val hashingSeedBytes: ByteArray by lazy {
        ByteBuffer.allocate(Long.SIZE_BYTES).apply { putLong(HASHING_SEED) }.array()
    }

    /**
     * Hashes all projects and returns the results.
     *
     * @param applicationDirectory The root directory of the application
     * @param useBuildLogicVersion Whether to include the build-logic version in the hash
     * @param useProjects Set of project paths to include (empty means all projects)
     * @param ciMetadata CI metadata for analytics
     * @return Result containing hashes for all projects
     */
    suspend fun hashProjects(
        applicationDirectory: Path,
        useBuildLogicVersion: Boolean,
        useProjects: Set<String>,
        ciMetadata: CiMetadata
    ): HashingServiceResult = coroutineScope {
        val startTime = Clock.systemUTC().millis()
        var result = HashingServiceResult(
            gitBranch = ciMetadata.gitBranch,
            gitSha = ciMetadata.gitSha,
            ciEnv = ciMetadata.ciEnv,
            buildId = ciMetadata.buildId,
            buildStepId = ciMetadata.buildStepId,
            buildJobId = ciMetadata.buildJobId,
            ciType = ciMetadata.ciType
        )

        try {
            logger.debug { "Determining projects" }
            val (projectsToHash, acquireGradleProjectsDuration) = measureTimedValue {
                gradleProjectsProvider.getProjectHashingInfos().getOrElse { error ->
                    logger.error(error) { "Failed to get gradle project info for hashing" }
                    return@coroutineScope result.copy(
                        totalDurationMs = Clock.systemUTC().millis() - startTime
                    )
                }
            }
            logger.debug { "Found ${projectsToHash.size} projects" }
            result = result.copy(
                acquireProjectsDurationMs = acquireGradleProjectsDuration.inWholeMilliseconds,
                countProjectsHashed = 0,
                countFilesHashed = 0
            )

            // Get the flow that contains the hash result for each project
            val (projectHashes, hashDuration) = measureTimedValue {
                projectsToHash.getHashFlow(
                    applicationDirectory,
                    useBuildLogicVersion,
                    useProjects
                ).onEach { hashingResult ->
                    result = result.copy(
                        countProjectsHashed = result.countProjectsHashed + if (hashingResult.hash.isNotEmpty()) 1 else 0,
                        countFilesHashed = result.countFilesHashed + hashingResult.countFilesHashed
                    )
                    logger.trace { "Hashed ${hashingResult.projectPath} in ${hashingResult.duration}" }
                }.toList()
                    .sortedBy { it.projectPath }
            }
            result = result.copy(
                hashProjectsDurationMs = hashDuration.inWholeMilliseconds,
                projectHashes = projectHashes
            )
        } finally {
            gradleProjectsProvider.cleanup()
            result = result.copy(
                totalDurationMs = Clock.systemUTC().millis() - startTime
            )
        }

        return@coroutineScope result
    }

    /**
     * Logs the result to the event stream.
     */
    suspend fun logResult(result: HashingServiceResult) {
        eventStream.sendResults(listOf(result))
    }

    private fun List<ProjectHashingInfo>.getHashFlow(
        applicationDirectory: Path,
        useBuildLogicVersion: Boolean,
        useProjects: Set<String>,
    ): Flow<ProjectHashingResult> {
        return channelFlow {
            // Get the properties
            val properties = Properties().apply {
                if (useBuildLogicVersion) {
                    withContext(ioDispatcher) {
                        applicationDirectory.resolve(GRADLE_PROPERTIES_FILE_NAME).inputStream().use(::load)
                    }
                }
            }

            // Store the byte array of the build-logic consumer version
            val buildLogicVersion =
                properties[BUILD_LOGIC_CONSUMER_KEY]?.toString()?.toByteArray()
            logger.debug { "Hashing project source files" }
            this@getHashFlow
                .filter { useProjects.isEmpty() || it.projectPath in useProjects }
                .forEach { projectHashingInfo ->
                    launch(defaultDispatcher) {
                        val messageDigest = MessageDigest.getInstance(SHA_256)
                        messageDigest.update(hashingSeedBytes)
                        buildLogicVersion?.let(messageDigest::update)

                        // Hash the project files
                        val (hashAndFilesHashedCount, duration) = measureTimedValue {
                            projectHashingInfo.filesToHash.hashSources(messageDigest)
                        }
                        val (hash, filesHashedCount) = hashAndFilesHashedCount
                        send(
                            ProjectHashingResult(
                                projectHashingInfo.projectPath,
                                hash,
                                duration,
                                filesHashedCount
                            )
                        )
                    }
                }
        }.flowOn(defaultDispatcher)
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
