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
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.eventstream.EventstreamService
import xyz.block.artifactswap.core.gradle.GradleProjectsProvider
import xyz.block.artifactswap.core.gradle.ProjectHashingInfo
import xyz.block.artifactswap.core.hashing.services.HashingEventStream
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the CLI command that verify picocli argument parsing
 * and proper wiring with the core ProjectHashService.
 */
class HashingCommandTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var fakeEventStream: FakeHashingEventStream
    private lateinit var fakeGradleProjectsProvider: FakeGradleProjectsProvider
    private lateinit var commandLine: CommandLine

    @BeforeEach
    fun setUp() {
        fakeEventStream = FakeHashingEventStream()
        fakeGradleProjectsProvider = FakeGradleProjectsProvider()
        commandLine = CommandLine(HashingCommand())
    }

    @Test
    fun `GIVEN valid CLI args WHEN executing THEN command parses args and runs hashing`() = runTest {
        val testFile = Path(tempDir.absolutePath, "test.txt")
        testFile.writeText("test content")

        val projectInfo = ProjectHashingInfo(
            projectPath = ":test-project",
            projectDirectory = Path(tempDir.absolutePath),
            filesToHash = sequenceOf(testFile)
        )

        fakeGradleProjectsProvider.projectHashingInfos = Result.success(listOf(projectInfo))

        val hashingOutputFile = Path(tempDir.absolutePath, "hashes.txt")

        val command = commandLine.parseArgs(
            "--hashing-output-file", hashingOutputFile.toString(),
            "--build_id", "build-123"
        ).commandSpec().commandLine().getCommand<HashingCommand>()

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
                single<HashingEventStream> { fakeEventStream }
                single<GradleProjectsProvider> { fakeGradleProjectsProvider }
            }
        )

        command.executeCommand(testApplication)

        // Verify that the hashing was called and completed successfully
        assertEquals(1, fakeEventStream.receivedResults.size)
        val result = fakeEventStream.receivedResults.first()
        assertEquals(1, result.countProjectsHashed)
        assertEquals(1, result.countFilesHashed)

        // Verify output file was created with correct content
        assertTrue(hashingOutputFile.toFile().exists())
        val lines = hashingOutputFile.readLines()
        assertEquals(1, lines.size)
        assertTrue(lines.first().startsWith(":test-project|"))
        assertTrue(fakeGradleProjectsProvider.cleanupCalled)
    }

    @Test
    fun `GIVEN multiple projects WHEN executing THEN hashes all projects and writes to file`() = runTest {
        val testFile1 = Path(tempDir.absolutePath, "test1.txt")
        testFile1.writeText("test content 1")
        val testFile2 = Path(tempDir.absolutePath, "test2.txt")
        testFile2.writeText("test content 2")

        val projectInfo1 = ProjectHashingInfo(
            projectPath = ":project1",
            projectDirectory = Path(tempDir.absolutePath),
            filesToHash = sequenceOf(testFile1)
        )
        val projectInfo2 = ProjectHashingInfo(
            projectPath = ":project2",
            projectDirectory = Path(tempDir.absolutePath),
            filesToHash = sequenceOf(testFile2)
        )

        fakeGradleProjectsProvider.projectHashingInfos = Result.success(listOf(projectInfo1, projectInfo2))

        val hashingOutputFile = Path(tempDir.absolutePath, "hashes.txt")

        val command = commandLine.parseArgs(
            "--hashing-output-file", hashingOutputFile.toString()
        ).commandSpec().commandLine().getCommand<HashingCommand>()

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
                single<HashingEventStream> { fakeEventStream }
                single<GradleProjectsProvider> { fakeGradleProjectsProvider }
            }
        )

        command.executeCommand(testApplication)

        // Verify output file content
        val lines = hashingOutputFile.readLines()
        assertEquals(2, lines.size)
        assertTrue(lines.any { it.startsWith(":project1|") })
        assertTrue(lines.any { it.startsWith(":project2|") })
    }

    @Test
    fun `GIVEN use-projects filter WHEN executing THEN only hashes specified projects`() = runTest {
        val testFile1 = Path(tempDir.absolutePath, "test1.txt")
        testFile1.writeText("test content 1")
        val testFile2 = Path(tempDir.absolutePath, "test2.txt")
        testFile2.writeText("test content 2")

        val projectInfo1 = ProjectHashingInfo(
            projectPath = ":project1",
            projectDirectory = Path(tempDir.absolutePath),
            filesToHash = sequenceOf(testFile1)
        )
        val projectInfo2 = ProjectHashingInfo(
            projectPath = ":project2",
            projectDirectory = Path(tempDir.absolutePath),
            filesToHash = sequenceOf(testFile2)
        )

        fakeGradleProjectsProvider.projectHashingInfos = Result.success(listOf(projectInfo1, projectInfo2))

        val hashingOutputFile = Path(tempDir.absolutePath, "hashes.txt")

        val command = commandLine.parseArgs(
            "--hashing-output-file", hashingOutputFile.toString(),
            "--use-projects", ":project1"
        ).commandSpec().commandLine().getCommand<HashingCommand>()

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
                single<HashingEventStream> { fakeEventStream }
                single<GradleProjectsProvider> { fakeGradleProjectsProvider }
            }
        )

        command.executeCommand(testApplication)

        // Verify only one project was hashed
        assertEquals(1, fakeEventStream.receivedResults.first().countProjectsHashed)
        val lines = hashingOutputFile.readLines()
        assertEquals(1, lines.size)
        assertTrue(lines.first().startsWith(":project1|"))
    }
}
