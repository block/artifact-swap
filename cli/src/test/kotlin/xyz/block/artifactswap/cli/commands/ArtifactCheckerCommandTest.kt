package xyz.block.artifactswap.cli.commands

import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.mockito.kotlin.mock
import picocli.CommandLine
import retrofit2.Response
import xyz.block.artifactswap.core.artifact_checker.models.ArtifactCheckerResult
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.eventstream.EventstreamService
import xyz.block.artifactswap.core.network.ArtifactoryService
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the CLI command that verify picocli argument parsing
 * and proper wiring with the core ArtifactCheckerService.
 */
class ArtifactCheckerCommandTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var fakeEventStream: FakeArtifactCheckerEventStream
    private lateinit var commandLine: CommandLine

    @BeforeEach
    fun setUp() {
        fakeEventStream = FakeArtifactCheckerEventStream()
        commandLine = CommandLine(ArtifactCheckerCommand())
    }

    @Test
    fun `GIVEN valid input files WHEN some artifacts missing THEN outputs missing artifacts to file`() = runTest {
        // Create hash file with project versions
        val hashFile = tempDir.toPath().resolve("hash.txt")
        hashFile.writeText(":project1|1.0.0\n:project2|2.0.0\n:project3|3.0.0")

        // Create input file with project:task entries
        val inputFile = tempDir.toPath().resolve("input.txt")
        inputFile.writeText(":project1:publish\n:project2:publish\n:project3:publish")

        // Create output file
        val outputFile = tempDir.toPath().resolve("output.txt")

        // Configure fake service: only project1 exists
        val fakeArtifactoryService = createFakeArtifactoryServiceForCli(
            setOf("project1" to "1.0.0")
        )

        val command = commandLine.parseArgs(
            "--dir=${tempDir.absolutePath}",
            "--hash-file=${hashFile}",
            "--input-file=${inputFile}",
            "--output-file=${outputFile}",
            "--build_id=build-123"
        ).commandSpec().commandLine().getCommand<ArtifactCheckerCommand>()

        val testApplication = koinApplication {
            allowOverride(true)
        }
        testApplication.modules(
            module {
                single(named("IO")) { kotlinx.coroutines.Dispatchers.Unconfined }
                single(named("Default")) { kotlinx.coroutines.Dispatchers.Unconfined }
                single(named("directory")) { Path(tempDir.absolutePath) }
                single<Eventstream>(named("analyticsModuleEventStream")) {
                    Eventstream(eventstreamService = mock<EventstreamService>())
                }
            }
        )
        command.init(testApplication)
        testApplication.modules(
            module {
                single<xyz.block.artifactswap.core.artifact_checker.services.ArtifactCheckerEventStream> { fakeEventStream }
                single<ArtifactoryService> { fakeArtifactoryService }
            }
        )

        command.executeCommand(testApplication)

        // Verify result was logged
        assertEquals(1, fakeEventStream.receivedResults.size)
        val result = fakeEventStream.receivedResults.first()
        assertEquals(ArtifactCheckerResult.SUCCESS, result.result)
        assertEquals(3, result.countProjectsToCheck)
        assertEquals(1, result.countArtifactsFound)
        assertEquals(2, result.missingArtifacts.size)

        // Verify output file contains missing artifacts
        val outputLines = outputFile.readLines()
        assertEquals(2, outputLines.size)
        assertTrue(outputLines.contains(":project2:publish"))
        assertTrue(outputLines.contains(":project3:publish"))
    }

    @Test
    fun `GIVEN all artifacts exist WHEN checking THEN outputs empty file`() = runTest {
        // Create hash file with project versions
        val hashFile = tempDir.toPath().resolve("hash.txt")
        hashFile.writeText(":project1|1.0.0\n:project2|2.0.0")

        // Create input file with project:task entries
        val inputFile = tempDir.toPath().resolve("input.txt")
        inputFile.writeText(":project1:publish\n:project2:publish")

        // Create output file
        val outputFile = tempDir.toPath().resolve("output.txt")

        // Configure fake service: all artifacts exist
        val fakeArtifactoryService = createFakeArtifactoryServiceForCli(
            setOf(
                "project1" to "1.0.0",
                "project2" to "2.0.0"
            )
        )

        val command = commandLine.parseArgs(
            "--dir=${tempDir.absolutePath}",
            "--hash-file=${hashFile}",
            "--input-file=${inputFile}",
            "--output-file=${outputFile}"
        ).commandSpec().commandLine().getCommand<ArtifactCheckerCommand>()

        val testApplication = koinApplication {
            allowOverride(true)
        }
        testApplication.modules(
            module {
                single(named("IO")) { kotlinx.coroutines.Dispatchers.Unconfined }
                single(named("Default")) { kotlinx.coroutines.Dispatchers.Unconfined }
                single(named("directory")) { Path(tempDir.absolutePath) }
                single<Eventstream>(named("analyticsModuleEventStream")) {
                    Eventstream(eventstreamService = mock<EventstreamService>())
                }
            }
        )
        command.init(testApplication)
        testApplication.modules(
            module {
                single<xyz.block.artifactswap.core.artifact_checker.services.ArtifactCheckerEventStream> { fakeEventStream }
                single<ArtifactoryService> { fakeArtifactoryService }
            }
        )

        command.executeCommand(testApplication)

        // Verify all artifacts found
        val result = fakeEventStream.receivedResults.first()
        assertEquals(ArtifactCheckerResult.SUCCESS, result.result)
        assertEquals(2, result.countProjectsToCheck)
        assertEquals(2, result.countArtifactsFound)
        assertTrue(result.missingArtifacts.isEmpty())

        // Verify output file is empty
        val outputLines = outputFile.readLines()
        assertTrue(outputLines.isEmpty())
    }

    @Test
    fun `GIVEN missing input file WHEN executing THEN returns FAILED_NO_INPUT_FILE result`() = runTest {
        // Create hash file
        val hashFile = tempDir.toPath().resolve("hash.txt")
        hashFile.writeText(":project1|1.0.0")

        // DO NOT create input file
        val inputFile = tempDir.toPath().resolve("input.txt")
        val outputFile = tempDir.toPath().resolve("output.txt")

        val fakeArtifactoryService = createFakeArtifactoryServiceForCli(emptySet())

        val command = commandLine.parseArgs(
            "--dir=${tempDir.absolutePath}",
            "--hash-file=${hashFile}",
            "--input-file=${inputFile}",
            "--output-file=${outputFile}"
        ).commandSpec().commandLine().getCommand<ArtifactCheckerCommand>()

        val testApplication = koinApplication {
            allowOverride(true)
        }
        testApplication.modules(
            module {
                single(named("IO")) { kotlinx.coroutines.Dispatchers.Unconfined }
                single(named("Default")) { kotlinx.coroutines.Dispatchers.Unconfined }
                single(named("directory")) { Path(tempDir.absolutePath) }
                single<Eventstream>(named("analyticsModuleEventStream")) {
                    Eventstream(eventstreamService = mock<EventstreamService>())
                }
            }
        )
        command.init(testApplication)
        testApplication.modules(
            module {
                single<xyz.block.artifactswap.core.artifact_checker.services.ArtifactCheckerEventStream> { fakeEventStream }
                single<ArtifactoryService> { fakeArtifactoryService }
            }
        )

        command.executeCommand(testApplication)

        // Verify FAILED_NO_INPUT_FILE result
        assertEquals(1, fakeEventStream.receivedResults.size)
        val result = fakeEventStream.receivedResults.first()
        assertEquals(ArtifactCheckerResult.FAILED_NO_INPUT_FILE, result.result)
    }

    @Test
    fun `GIVEN nested project paths WHEN checking THEN converts paths correctly`() = runTest {
        // Create hash file with nested project paths
        val hashFile = tempDir.toPath().resolve("hash.txt")
        hashFile.writeText(":foo:bar:baz|1.0.0")

        // Create input file
        val inputFile = tempDir.toPath().resolve("input.txt")
        inputFile.writeText(":foo:bar:baz:publish")

        // Create output file
        val outputFile = tempDir.toPath().resolve("output.txt")

        // Configure fake service with converted artifact name
        val fakeArtifactoryService = createFakeArtifactoryServiceForCli(
            setOf("foo_bar_baz" to "1.0.0")
        )

        val command = commandLine.parseArgs(
            "--dir=${tempDir.absolutePath}",
            "--hash-file=${hashFile}",
            "--input-file=${inputFile}",
            "--output-file=${outputFile}"
        ).commandSpec().commandLine().getCommand<ArtifactCheckerCommand>()

        val testApplication = koinApplication {
            allowOverride(true)
        }
        testApplication.modules(
            module {
                single(named("IO")) { kotlinx.coroutines.Dispatchers.Unconfined }
                single(named("Default")) { kotlinx.coroutines.Dispatchers.Unconfined }
                single(named("directory")) { Path(tempDir.absolutePath) }
                single<Eventstream>(named("analyticsModuleEventStream")) {
                    Eventstream(eventstreamService = mock<EventstreamService>())
                }
            }
        )
        command.init(testApplication)
        testApplication.modules(
            module {
                single<xyz.block.artifactswap.core.artifact_checker.services.ArtifactCheckerEventStream> { fakeEventStream }
                single<ArtifactoryService> { fakeArtifactoryService }
            }
        )

        command.executeCommand(testApplication)

        // Verify artifact was found
        val result = fakeEventStream.receivedResults.first()
        assertEquals(ArtifactCheckerResult.SUCCESS, result.result)
        assertEquals(1, result.countProjectsToCheck)
        assertEquals(1, result.countArtifactsFound)
        assertTrue(result.missingArtifacts.isEmpty())
    }
}

/**
 * Fake ArtifactoryEndpoints for CLI integration tests
 */
class FakeArtifactoryEndpointsForCli : xyz.block.artifactswap.core.network.ArtifactoryEndpoints {
    var existingArtifacts = setOf<Pair<String, String>>()

    override suspend fun getMavenMetadata(repo: String, artifact: String): Response<xyz.block.artifactswap.core.maven.Metadata> {
        return Response.error(404, okhttp3.ResponseBody.create(null, "Not needed for these tests"))
    }

    override suspend fun getPom(repo: String, artifact: String, version: String): Response<xyz.block.artifactswap.core.maven.Project> {
        // Return a basic POM with "jar" packaging for testing
        return if (existingArtifacts.contains(artifact to version)) {
            Response.success(
                xyz.block.artifactswap.core.maven.Project(
                    groupId = "com.squareup.register.sandbags",
                    artifactId = artifact,
                    version = version,
                    name = artifact,
                    dependencyManagement = xyz.block.artifactswap.core.maven.DependencyManagement(
                        dependencies = xyz.block.artifactswap.core.maven.Dependencies(emptyList())
                    ),
                    packaging = "jar"
                )
            )
        } else {
            Response.error(404, okhttp3.ResponseBody.create(null, "POM not found"))
        }
    }

    override suspend fun headArtifact(
        repo: String,
        artifact: String,
        version: String,
        packaging: String
    ): Response<Void> {
        return if (existingArtifacts.contains(artifact to version)) {
            Response.success(null)
        } else {
            Response.error(404, okhttp3.ResponseBody.create(null, "Artifact not found"))
        }
    }

    override suspend fun pushMetadata(repo: String, artifact: String, metadata: xyz.block.artifactswap.core.maven.Metadata): Response<Unit> {
        throw NotImplementedError("Not needed for these tests")
    }

    override suspend fun pushPom(
        repo: String,
        artifact: String,
        version: String,
        filename: String,
        project: xyz.block.artifactswap.core.maven.Project
    ): Response<Unit> {
        throw NotImplementedError("Not needed for these tests")
    }

    override suspend fun getFile(
        repo: String,
        group: String,
        artifact: String,
        version: String,
        ext: String
    ): Response<okhttp3.ResponseBody> {
        return Response.error(404, okhttp3.ResponseBody.create(null, "Not needed for these tests"))
    }
}

fun createFakeArtifactoryServiceForCli(existingArtifacts: Set<Pair<String, String>> = emptySet()): xyz.block.artifactswap.core.network.ArtifactoryService {
    val fakeEndpoints = FakeArtifactoryEndpointsForCli().apply {
        this.existingArtifacts = existingArtifacts
    }
    return xyz.block.artifactswap.core.network.ArtifactoryService(fakeEndpoints)
}
