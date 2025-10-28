package xyz.block.artifactswap.cli.commands

import xyz.block.artifactswap.core.gradle.GradlePropertiesProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.mockito.kotlin.mock
import org.mockito.kotlin.wheneverBlocking
import picocli.CommandLine
import xyz.block.artifactswap.core.download.models.Artifact
import xyz.block.artifactswap.core.download.models.ArtifactDownloaderResult
import xyz.block.artifactswap.core.download.services.ArtifactDownloaderEventStream
import xyz.block.artifactswap.core.download.services.ArtifactRepository
import xyz.block.artifactswap.core.download.services.ArtifactSyncBomLoader
import xyz.block.artifactswap.core.gradle.GradleProjectsProvider
import java.io.File
import kotlin.test.assertEquals

/**
 * Integration tests for the CLI command that verify picocli argument parsing
 * and proper wiring with the core ArtifactDownloader.
 */
class ArtifactDownloaderCommandTest {

    @TempDir
    lateinit var tempDir: File
    val mockArtifactSyncBomLoader = mock<ArtifactSyncBomLoader>()
    lateinit var fakeArtifactRepository: FakeArtifactRepository
    lateinit var fakeEventStream: FakeEventStream
    lateinit var projectsProvider: GradleProjectsProvider
    lateinit var propertiesProvider: GradlePropertiesProvider
    lateinit var commandLine: CommandLine

    @BeforeEach
    fun setUp() {
        fakeArtifactRepository = FakeArtifactRepository()
        fakeEventStream = FakeEventStream()
        projectsProvider = FakeGradleProjectsProvider(emptyList())
        propertiesProvider = FakeGradlePropertiesProvider()
        commandLine = CommandLine(ArtifactDownloaderCommand())
    }

    private companion object {
        val FAKE_ARTIFACTS = listOf(
            Artifact("com.squareup", "okhttp", "4.9.0"),
            Artifact("com.squareup", "retrofit", "2.9.0"),
        )

        const val FAKE_BOM_VERSION = "abdcd12345"
    }

    @Test
    fun `GIVEN valid CLI args WHEN executing THEN command parses args and runs downloader`() = runTest {
        fakeArtifactRepository.getBomResult = Result.success(FAKE_ARTIFACTS)

        val command = commandLine.parseArgs(
            "--bom-version", FAKE_BOM_VERSION,
            "--maven-local-path", tempDir.absolutePath
        ).commandSpec().commandLine().getCommand<ArtifactDownloaderCommand>()

        val testApplication = koinApplication()
        command.init(testApplication)
        testApplication.modules(
            module {
                single(named("IO")) { kotlinx.coroutines.Dispatchers.Unconfined }
                single<ArtifactSyncBomLoader> { mockArtifactSyncBomLoader }
                single<ArtifactRepository> { fakeArtifactRepository }
                single<ArtifactDownloaderEventStream> { fakeEventStream }
                single<GradleProjectsProvider> { projectsProvider }
                single<GradlePropertiesProvider> { propertiesProvider }
            }
        )

        command.executeCommand(testApplication)

        // Verify that the downloader was called and completed successfully
        assertEquals(1, fakeEventStream.receivedEvents.size)
        val event = fakeEventStream.receivedEvents.first()
        assertEquals(ArtifactDownloaderResult.SUCCESS, event.result)
    }

    @Test
    fun `GIVEN missing bom version WHEN executing THEN command finds best bom version`() = runTest {
        wheneverBlocking { mockArtifactSyncBomLoader.findBestBomVersion() }
            .thenReturn(Result.success(FAKE_BOM_VERSION))
        fakeArtifactRepository.getBomResult = Result.success(FAKE_ARTIFACTS)

        val command = commandLine.parseArgs(
            "--maven-local-path", tempDir.absolutePath
        ).commandSpec().commandLine().getCommand<ArtifactDownloaderCommand>()

        val testApplication = koinApplication()
        command.init(testApplication)
        testApplication.modules(
            module {
                single(named("IO")) { kotlinx.coroutines.Dispatchers.Unconfined }
                single<ArtifactSyncBomLoader> { mockArtifactSyncBomLoader }
                single<ArtifactRepository> { fakeArtifactRepository }
                single<ArtifactDownloaderEventStream> { fakeEventStream }
                single<GradleProjectsProvider> { projectsProvider }
                single<GradlePropertiesProvider> { propertiesProvider }
            }
        )

        command.executeCommand(testApplication)

        assertEquals(1, fakeEventStream.receivedEvents.size)
        val event = fakeEventStream.receivedEvents.first()
        assertEquals(ArtifactDownloaderResult.SUCCESS, event.result)
    }
}
