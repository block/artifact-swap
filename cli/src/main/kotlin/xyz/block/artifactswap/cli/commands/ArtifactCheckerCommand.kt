package xyz.block.artifactswap.cli.commands

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.kotlin.logger
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import picocli.CommandLine
import xyz.block.artifactswap.cli.AbstractArtifactSwapCommand
import xyz.block.artifactswap.cli.options.ArtifactCheckerOptions
import xyz.block.artifactswap.cli.options.CiConfigurationOptions
import xyz.block.artifactswap.core.artifact_checker.ArtifactCheckerService
import xyz.block.artifactswap.core.artifact_checker.CiMetadata
import xyz.block.artifactswap.core.artifact_checker.di.ArtifactCheckerServiceConfig
import xyz.block.artifactswap.core.artifact_checker.di.artifactCheckerService
import xyz.block.artifactswap.core.artifact_checker.di.artifactCheckerServiceModules
import xyz.block.artifactswap.core.artifact_checker.models.ArtifactCheckerResult
import xyz.block.artifactswap.core.artifact_checker.models.ArtifactCheckerServiceResult
import java.io.IOException
import java.nio.file.Path
import java.time.Clock
import kotlin.io.path.createFile
import kotlin.io.path.notExists
import kotlin.io.path.useLines
import kotlin.io.path.writeLines
import kotlin.time.measureTimedValue

/**
 * CLI command for checking if artifacts exist in Artifactory.
 */
@CommandLine.Command(
    name = "artifact-checker",
    description = ["Checks the given list of paths with tasks for an artifact in artifactory."]
)
class ArtifactCheckerCommand : AbstractArtifactSwapCommand() {

    @CommandLine.Mixin
    private lateinit var artifactCheckerOptions: ArtifactCheckerOptions

    @CommandLine.Mixin
    private lateinit var ciOptions: CiConfigurationOptions

    private lateinit var artifactCheckerService: ArtifactCheckerService
    private lateinit var ioDispatcher: CoroutineDispatcher

    override fun init(application: KoinApplication) {
        val config = ArtifactCheckerServiceConfig()
        application.modules(artifactCheckerServiceModules(application, config))
    }

    override suspend fun executeCommand(application: KoinApplication) {
        artifactCheckerService = application.artifactCheckerService
        ioDispatcher = application.koin.get(named("IO"))

        val startTime = Clock.systemUTC().millis()

        // Check if input file exists
        if (artifactCheckerOptions.inputFile.notExists()) {
            logger.warn { "No input file found" }
            val result = ArtifactCheckerServiceResult(
                result = ArtifactCheckerResult.FAILED_NO_INPUT_FILE,
                totalDurationMs = Clock.systemUTC().millis() - startTime
            )
            artifactCheckerService.logResult(result)
            return
        }

        // Read hash file and input file
        val (projectTaskVersions, determineProjectsDuration) = measureTimedValue {
            try {
                withContext(ioDispatcher) {
                    val hashFile = artifactCheckerOptions.hashFile.useLines { lines ->
                        lines.flatMap { line ->
                            line.split('|')
                                .takeIf { it.size == 2 }
                                ?.zipWithNext() ?: emptyList()
                        }.toMap()
                    }

                    artifactCheckerOptions.inputFile.useLines { lines ->
                        lines.mapNotNull { line ->
                            val path = line.substringBeforeLast(':')
                            val task = line.substringAfterLast(':')
                            val version = hashFile[path] ?: return@mapNotNull null
                            Triple(path, task, version)
                        }.toList()
                    }
                }
            } catch (e: IOException) {
                logger.error(e) { "Failed reading hash file or input file" }
                val result = ArtifactCheckerServiceResult(
                    result = ArtifactCheckerResult.FAILED_READING_INPUT_FILES,
                    totalDurationMs = Clock.systemUTC().millis() - startTime
                )
                artifactCheckerService.logResult(result)
                return
            }
        }

        val ciMetadata = CiMetadata(
            gitBranch = System.getenv("GIT_BRANCH").orEmpty(),
            gitSha = System.getenv("GIT_COMMIT").orEmpty(),
            ciEnv = System.getenv("KOCHIKU_ENV").orEmpty(),
            buildId = ciOptions.buildId,
            buildStepId = ciOptions.buildStepId,
            buildJobId = ciOptions.buildJobId,
            ciType = ciOptions.ciType
        )

        // Check artifacts
        var result = artifactCheckerService.checkArtifacts(
            projectTaskVersions = projectTaskVersions,
            ciMetadata = ciMetadata
        )

        // Update with determine projects duration
        result = result.copy(
            determineProjectsDurationMs = determineProjectsDuration.inWholeMilliseconds
        )

        // Write output file
        try {
            withContext(ioDispatcher) {
                with(artifactCheckerOptions.outputFile) {
                    if (notExists()) {
                        createFile()
                    }
                    writeLines(lines = result.missingArtifacts)
                }
            }
        } catch (e: IOException) {
            logger.error(e) { "Failed writing output file" }
            result = result.copy(
                result = ArtifactCheckerResult.FAILED_WRITING_OUTPUT
            )
        }

        // Log the result
        artifactCheckerService.logResult(result)
    }
}
