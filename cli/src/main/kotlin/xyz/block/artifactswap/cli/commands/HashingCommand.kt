package xyz.block.artifactswap.cli.commands

import org.apache.logging.log4j.kotlin.logger
import org.koin.core.KoinApplication
import picocli.CommandLine
import picocli.CommandLine.Mixin
import xyz.block.artifactswap.cli.AbstractArtifactSwapCommand
import xyz.block.artifactswap.cli.options.CiConfigurationOptions
import xyz.block.artifactswap.cli.options.HashingOptions
import xyz.block.artifactswap.core.hashing.CiMetadata
import xyz.block.artifactswap.core.hashing.ProjectHashService
import xyz.block.artifactswap.core.hashing.di.ProjectHashServiceConfig
import xyz.block.artifactswap.core.hashing.di.projectHashService
import xyz.block.artifactswap.core.hashing.di.projectHashServiceModules
import java.nio.file.Path
import kotlin.io.path.appendLines
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists

@CommandLine.Command(
    name = "hashing",
    description = ["Generates a dictionary of project paths to a hash of project source files"]
)
class HashingCommand : AbstractArtifactSwapCommand() {

    @Mixin
    private lateinit var hashingOptions: HashingOptions

    @Mixin
    private lateinit var ciOptions: CiConfigurationOptions

    private lateinit var projectHashService: ProjectHashService
    private lateinit var hashingFile: Path

    override fun init(application: KoinApplication) {
        val config = ProjectHashServiceConfig()
        application.modules(projectHashServiceModules(application, config))
    }

    override suspend fun executeCommand(application: KoinApplication) {
        projectHashService = application.projectHashService

        val applicationDirectory = application.koin.get<Path>(org.koin.core.qualifier.named("directory"))

        // Default hashing file location is ".gradle/sandbagHashes/sandbagHashing.out"
        hashingFile = hashingOptions.hashingFile ?: applicationDirectory
            .resolve(".gradle")
            .resolve("sandbagHashes")
            .createDirectories()
            .resolve("sandbagHashing.out")

        logger.info { "Starting project hashing" }

        // Prepare the hashing file
        with(hashingFile) {
            deleteIfExists()
            createParentDirectories()
            createFile()
        }

        val useProjects = hashingOptions.useProjects
            .asSequence()
            .map {
                val path = it.trim().replace("[\\\\/]".toRegex(), ":")
                if (!path.startsWith(':')) {
                    ":$path"
                } else {
                    path
                }
            }
            .toSet()

        val ciMetadata = CiMetadata(
            buildId = ciOptions.buildId,
            buildStepId = ciOptions.buildStepId,
            buildJobId = ciOptions.buildJobId,
            ciType = ciOptions.ciType
        )

        val result = projectHashService.hashProjects(
            applicationDirectory = applicationDirectory,
            useBuildLogicVersion = hashingOptions.useBuildLogicVersion,
            useProjects = useProjects,
            ciMetadata = ciMetadata
        )

        logger.info { "Project hashing completed: ${result.countProjectsHashed} projects hashed, ${result.countFilesHashed} files" }

        // Write results to file
        val hashList = result.projectHashes.map { hashResult ->
            "${hashResult.projectPath}|${hashResult.hash}"
        }
        hashingFile.appendLines(hashList)

        projectHashService.logResult(result)

        println("Done!")
    }
}
