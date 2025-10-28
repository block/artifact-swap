package xyz.block.artifactswap.cli.commands

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.mockito.kotlin.mock
import picocli.CommandLine
import retrofit2.Response
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.eventstream.EventstreamService
import xyz.block.artifactswap.core.maven.Metadata
import xyz.block.artifactswap.core.maven.Versioning
import xyz.block.artifactswap.core.maven.Versions
import xyz.block.artifactswap.core.publisher.models.BomPublishingResult
import xyz.block.artifactswap.core.publisher.services.BomPublisherEventStream
import xyz.block.artifactswap.core.publisher.services.ProjectHashReader
import xyz.block.artifactswap.core.network.ArtifactoryEndpoints
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the CLI command that verify picocli argument parsing
 * and proper wiring with the core BomPublisher.
 */
class BomPublishingCommandTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var fakeEventStream: FakeBomPublisherEventStream
    private lateinit var fakeArtifactoryEndpoints: FakeArtifactoryEndpoints
    private lateinit var fakeHashReader: FakeProjectHashReader
    private lateinit var commandLine: CommandLine

    @BeforeEach
    fun setUp() {
        fakeEventStream = FakeBomPublisherEventStream()
        fakeArtifactoryEndpoints = FakeArtifactoryEndpoints()
        fakeHashReader = FakeProjectHashReader()
        commandLine = CommandLine(BomPublishingCommand())
    }

    @Test
    fun `GIVEN valid CLI args WHEN executing THEN command parses args and runs publisher`() = runTest {
        // Create a hash file
        val hashFile = Path(tempDir.absolutePath, "hashes.txt")
        hashFile.writeText("project1|version1\nproject2|version2")

        fakeHashReader.projectHashes = Result.success(
            mapOf(
                "project1" to "version1",
                "project2" to "version2"
            )
        )

        // Set up metadata responses
        fakeArtifactoryEndpoints.metadataResponses["project1"] = Response.success(
            Metadata(
                groupId = "xyz.block.artifactswap.artifacts",
                artifactId = "project1",
                versioning = Versioning(
                    latest = "version1",
                    release = "version1",
                    versions = Versions(listOf("version1")),
                    lastUpdated = 0
                )
            )
        )

        val command = commandLine.parseArgs(
            "--bom-version", "1.0.0",
            "--hash-file-location", hashFile.toString(),
            "--build_id", "build-123"
        ).commandSpec().commandLine().getCommand<BomPublishingCommand>()

        val testApplication = koinApplication {
            allowOverride(true)
        }
        testApplication.modules(
            module {
                single(named("IO")) { kotlinx.coroutines.Dispatchers.Unconfined }
                single(named("dryRun")) { false }
                single<Eventstream>(named("analyticsModuleEventStream")) {
                    Eventstream(eventstreamService = mock<EventstreamService>())
                }
                single<ArtifactoryEndpoints> { fakeArtifactoryEndpoints }
            }
        )
        command.init(testApplication)
        testApplication.modules(
            module {
                single<BomPublisherEventStream> { fakeEventStream }
                single<ProjectHashReader> { fakeHashReader }
            }
        )

        command.executeCommand(testApplication)

        // Verify that the publisher was called and completed successfully
        assertEquals(1, fakeEventStream.receivedResults.size)
        val result = fakeEventStream.receivedResults.first()
        assertTrue(result.result in listOf(
            BomPublishingResult.SUCCESS_BOM_AND_METADATA_PUBLISHED,
            BomPublishingResult.SUCCESS_BOM_PUBLISHED_METADATA_NO_UPDATE,
            BomPublishingResult.SUCCESS_BOM_PUBLISHED_METADATA_FAILED
        ))
    }

    @Test
    fun `GIVEN published artifacts WHEN executing THEN creates BOM with correct dependencies`() = runTest {
        val hashFile = Path(tempDir.absolutePath, "hashes.txt")
        hashFile.writeText("artifact1|v1\nartifact2|v2")

        fakeHashReader.projectHashes = Result.success(
            mapOf(
                "artifact1" to "v1",
                "artifact2" to "v2"
            )
        )

        fakeArtifactoryEndpoints.metadataResponses["artifact1"] = Response.success(
            Metadata(
                groupId = "xyz.block.artifactswap.artifacts",
                artifactId = "artifact1",
                versioning = Versioning(
                    latest = "v1",
                    release = "v1",
                    versions = Versions(listOf("v1")),
                    lastUpdated = 0
                )
            )
        )
        fakeArtifactoryEndpoints.metadataResponses["artifact2"] = Response.success(
            Metadata(
                groupId = "xyz.block.artifactswap.artifacts",
                artifactId = "artifact2",
                versioning = Versioning(
                    latest = "v2",
                    release = "v2",
                    versions = Versions(listOf("v2")),
                    lastUpdated = 0
                )
            )
        )

        val command = commandLine.parseArgs(
            "--bom-version", "2.0.0",
            "--hash-file-location", hashFile.toString()
        ).commandSpec().commandLine().getCommand<BomPublishingCommand>()

        val testApplication = koinApplication {
            allowOverride(true)
        }
        testApplication.modules(
            module {
                single(named("IO")) { kotlinx.coroutines.Dispatchers.Unconfined }
                single(named("dryRun")) { false }
                single<Eventstream>(named("analyticsModuleEventStream")) {
                    Eventstream(eventstreamService = mock<EventstreamService>())
                }
                single<ArtifactoryEndpoints> { fakeArtifactoryEndpoints }
            }
        )
        command.init(testApplication)
        testApplication.modules(
            module {
                single<BomPublisherEventStream> { fakeEventStream }
                single<ProjectHashReader> { fakeHashReader }
            }
        )

        command.executeCommand(testApplication)

        // Verify the published BOM
        assertEquals(1, fakeArtifactoryEndpoints.pushedPoms.size)
        val pushedPom = fakeArtifactoryEndpoints.pushedPoms.first()
        assertEquals("bom", pushedPom.artifactId)
        assertEquals("2.0.0", pushedPom.version)
        assertEquals(2, pushedPom.dependencyManagement.dependencies.dependency.size)
    }
}
