package xyz.block.artifactswap.core.download

import xyz.block.artifactswap.core.gradle.GradlePropertiesProvider
import xyz.block.artifactswap.core.gradle.GradleProjectsProvider
import xyz.block.artifactswap.core.gradle.ProjectHashingInfo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.wheneverBlocking
import xyz.block.artifactswap.core.download.models.Artifact
import xyz.block.artifactswap.core.download.models.ArtifactDownloaderResult.FAILED_TO_DOWNLOAD_BOM
import xyz.block.artifactswap.core.download.models.ArtifactDownloaderResult.FAILED_TO_FIND_VALID_BOM_VERSION
import xyz.block.artifactswap.core.download.models.ArtifactDownloaderResult.MANY_DOWNLOADS_FAILED
import xyz.block.artifactswap.core.download.models.ArtifactDownloaderResult.MANY_INSTALLS_FAILED
import xyz.block.artifactswap.core.download.models.ArtifactDownloaderResult.SUCCESS
import xyz.block.artifactswap.core.download.models.DownloadFileType
import xyz.block.artifactswap.core.download.models.InstallArtifactFilesResult
import xyz.block.artifactswap.core.download.models.LocalArtifactState
import xyz.block.artifactswap.core.download.services.ArtifactSyncBomLoader
import xyz.block.artifactswap.core.download.services.SQUARE_PUBLIC_REPO
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class ArtifactDownloaderTest {

    @TempDir
    lateinit var tempDir: File
    val mockArtifactSyncBomLoader = mock<ArtifactSyncBomLoader>()
    lateinit var fakeArtifactRepository: FakeArtifactRepository
    lateinit var fakeEventStream: FakeEventStream
    lateinit var projectsProvider: GradleProjectsProvider
    lateinit var propertiesProvider: GradlePropertiesProvider
    lateinit var downloader: ArtifactDownloader

    @BeforeEach
    fun setUp() {
        fakeArtifactRepository = FakeArtifactRepository()
        fakeEventStream = FakeEventStream()
        projectsProvider = FakeGradleProjectsProvider(emptyList())
        propertiesProvider = FakeGradlePropertiesProvider()
        downloader = ArtifactDownloader(
            bomLoader = mockArtifactSyncBomLoader,
            artifactEventStream = fakeEventStream,
            artifactRepository = fakeArtifactRepository,
            settingsGradleProjectsProvider = projectsProvider,
            gradlePropertiesProvider = propertiesProvider,
        )
    }

    private companion object {
        val FAKE_ARTIFACTS = listOf(
            Artifact("com.squareup", "okhttp", "4.9.0"),
            Artifact("com.squareup", "retrofit", "2.9.0"),
            Artifact("com.squareup", "moshi", "1.9.0"),
            Artifact("com.squareup", "picasso", "3.9.0"),
            Artifact("com.squareup", "leakcanary", "2.9.0"),
            Artifact("com.squareup", "sqlbrite", "1.9.0"),
        )

        // the all-protos artifact is an implicit item to download
        const val ALL_PROTOS_COUNT = 1

        const val FAKE_BOM_VERSION = "abdcd12345"
    }

    @Test
    fun `GIVEN can't fetch bom WHEN executing THEN log failure and stop`() = runTest {
        fakeArtifactRepository.getBomResult = Result.failure(Exception("Failed to fetch BOM"))
        val result = downloader.downloadAndInstallArtifacts(
            bomVersion = FAKE_BOM_VERSION,
            settingsGradleFile = Path(tempDir.absolutePath)
        )

        assertEquals(FAILED_TO_DOWNLOAD_BOM, result.result)
        assertEquals(1, fakeEventStream.receivedEvents.size)
    }

    @Test
    fun `GIVEN requested bom not found WHEN executing THEN only download all-protos, success result`() = runTest {
        // we don't find anything at that bom version
        fakeArtifactRepository.getBomResult = Result.success(emptyList())
        val result = downloader.downloadAndInstallArtifacts(
            bomVersion = FAKE_BOM_VERSION,
            settingsGradleFile = Path(tempDir.absolutePath)
        )

        assertEquals(SUCCESS, result.result)
        assertEquals(ALL_PROTOS_COUNT, result.countArtifactsToDownload)
        assertEquals(1, fakeEventStream.receivedEvents.size)
    }

    @Test
    fun `GIVEN no bom provided WHEN executing THEN finds most recent commit with bom and downloads using that`() = runTest {
        wheneverBlocking { mockArtifactSyncBomLoader.findBestBomVersion() }
            .thenReturn(Result.success(FAKE_BOM_VERSION))
        fakeArtifactRepository.getBomResult = Result.success(FAKE_ARTIFACTS)

        val result = downloader.downloadAndInstallArtifacts(
            bomVersion = "", // empty means find best
            settingsGradleFile = Path(tempDir.absolutePath)
        )

        assertEquals(SUCCESS, result.result)
        assertEquals(
            FAKE_ARTIFACTS.size + ALL_PROTOS_COUNT,
            result.countArtifactsToDownload
        )
        assertEquals(1, fakeEventStream.receivedEvents.size)
    }

    @Test
    fun `GIVEN no bom provided AND fails finding one WHEN executing THEN returns failure with appropriate result type`() = runTest {
        wheneverBlocking { mockArtifactSyncBomLoader.findBestBomVersion() }
            .thenReturn(Result.failure(Exception("Failed to find bom version")))

        val result = downloader.downloadAndInstallArtifacts(
            bomVersion = "",
            settingsGradleFile = Path(tempDir.absolutePath)
        )

        assertEquals(FAILED_TO_FIND_VALID_BOM_VERSION, result.result)
        assertEquals(1, fakeEventStream.receivedEvents.size)
    }

    @Test
    fun `GIVEN all services working, no artifacts installed WHEN executing THEN download and install all artifacts`() = runTest {
        fakeArtifactRepository.getBomResult = Result.success(FAKE_ARTIFACTS)
        val result = downloader.downloadAndInstallArtifacts(
            bomVersion = FAKE_BOM_VERSION,
            settingsGradleFile = Path(tempDir.absolutePath)
        )

        assertEquals(SUCCESS, result.result)
        assertEquals(FAKE_ARTIFACTS.size + ALL_PROTOS_COUNT, result.countArtifactsToDownload)
        assertEquals(
            FAKE_ARTIFACTS.size * DownloadFileType.entries.size,
            result.countSuccessfulDownloadedArtifactFiles.toInt()
        )
        assertEquals(0, result.countFailedDownloadedArtifactFiles.toInt())
        assertEquals(
            FAKE_ARTIFACT_DOWNLOAD_SIZE_MB * FAKE_ARTIFACTS.size * DownloadFileType.entries.size,
            result.totalDownloadSizeMb
        )
        assertEquals(FAKE_ARTIFACTS.size, result.countSuccessfulInstalledArtifacts.toInt())
        assertEquals(0, result.countFailedInstalledArtifacts.toInt())

        // validate all durations are set to a non-negative value
        listOf(
            result.totalDurationMs,
            result.getArtifactsToDownloadDurationMs,
            result.p50DownloadTimeMs,
            result.p90DownloadTimeMs,
            result.p99DownloadTimeMs,
            result.maxDownloadTimeMs,
            result.p50InstallTimeMs,
            result.p90InstallTimeMs,
            result.p99InstallTimeMs,
            result.maxInstallTimeMs,
        ).forEach {
            assertNotEquals(-1, it)
        }
        assertEquals(1, fakeEventStream.receivedEvents.size)
    }

    @Test
    fun `GIVEN many download failures WHEN executing THEN reports as result`() = runTest {
        // get n/2 random indices to mark for failure
        FAKE_ARTIFACTS.indices.shuffled().take(FAKE_ARTIFACTS.size / 2)
            .forEach { fakeArtifactRepository.markArtifactForDownloadFailure(FAKE_ARTIFACTS[it]) }
        fakeArtifactRepository.getBomResult = Result.success(FAKE_ARTIFACTS)

        val result = downloader.downloadAndInstallArtifacts(
            bomVersion = FAKE_BOM_VERSION,
            settingsGradleFile = Path(tempDir.absolutePath)
        )

        assertEquals(MANY_DOWNLOADS_FAILED, result.result)
        assertEquals(FAKE_ARTIFACTS.size + ALL_PROTOS_COUNT, result.countArtifactsToDownload)
        assertEquals(
            (FAKE_ARTIFACTS.size / 2) * DownloadFileType.entries.size,
            result.countSuccessfulDownloadedArtifactFiles.toInt()
        )
        assertEquals(FAKE_ARTIFACTS.size / 2, result.countSuccessfulInstalledArtifacts.toInt())
        assertEquals(1, fakeEventStream.receivedEvents.size)
    }

    @Test
    fun `GIVEN many install failures WHEN executing THEN reports as result`() = runTest {
        // get n/2 random indices to mark for failure
        FAKE_ARTIFACTS.indices.shuffled().take(FAKE_ARTIFACTS.size / 2)
            .forEach {
                fakeArtifactRepository.setInstallArtifactResult(
                    FAKE_ARTIFACTS[it],
                    InstallArtifactFilesResult.Failure(FAKE_FAILURE_DURATION)
                )
            }
        fakeArtifactRepository.getBomResult = Result.success(FAKE_ARTIFACTS)

        val result = downloader.downloadAndInstallArtifacts(
            bomVersion = FAKE_BOM_VERSION,
            settingsGradleFile = Path(tempDir.absolutePath)
        )

        assertEquals(MANY_INSTALLS_FAILED, result.result)
        assertEquals(FAKE_ARTIFACTS.size + ALL_PROTOS_COUNT, result.countArtifactsToDownload)
        assertEquals(
            FAKE_ARTIFACTS.size * DownloadFileType.entries.size,
            result.countSuccessfulDownloadedArtifactFiles.toInt(),
        )
        assertEquals(FAKE_ARTIFACTS.size / 2, result.countSuccessfulInstalledArtifacts.toInt())
        assertEquals(1, fakeEventStream.receivedEvents.size)
    }

    @Test
    fun `GIVEN some artifacts already installed WHEN executing THEN does not download those artifacts`() = runTest {
        val indices = FAKE_ARTIFACTS.indices.shuffled().take(FAKE_ARTIFACTS.size / 2)
        val alreadyDownloadedFileType = DownloadFileType.JAR
        val artifactsPresentLocally = indices
            .map {
                val artifact = FAKE_ARTIFACTS[it]
                fakeArtifactRepository.setLocalArtifactState(
                    artifact,
                    alreadyDownloadedFileType,
                    LocalArtifactState.INSTALLED
                )
                artifact to alreadyDownloadedFileType
            }
        fakeArtifactRepository.getBomResult = Result.success(FAKE_ARTIFACTS)

        val result = downloader.downloadAndInstallArtifacts(
            bomVersion = FAKE_BOM_VERSION,
            settingsGradleFile = Path(tempDir.absolutePath)
        )

        assertEquals(SUCCESS, result.result)
        val downloadedArtifactFiles = fakeArtifactRepository.requestedArtifactFilesForDownload.toSet()
        artifactsPresentLocally.map { (localArtifact, localArtifactFileType) ->
            assertFalse { downloadedArtifactFiles.contains(localArtifact to localArtifactFileType) }
        }
        assertEquals(1, fakeEventStream.receivedEvents.size)
    }

    @Test
    fun `GIVEN protos not already installed WHEN executing THEN downloads the protos`() = runTest {
        // Fake repo defaults to indicating that protos are downloaded already
        fakeArtifactRepository.localArtifactStateCache.clear()
        fakeArtifactRepository.getBomResult = Result.success(emptyList())
        val fakeProtosProjects = listOf(
            ProjectHashingInfo(":foo:public", Paths.get("foo/public"), emptySequence()),
            ProjectHashingInfo(":bar:public", Paths.get("bar/public"), emptySequence()),
        )
        // Total count to be downloaded is the protos build projects plus 1 for `all-protos`
        val totalProtosProjects = fakeProtosProjects.size + 1
        projectsProvider = FakeGradleProjectsProvider(fakeProtosProjects)
        downloader = ArtifactDownloader(
            bomLoader = mockArtifactSyncBomLoader,
            artifactEventStream = fakeEventStream,
            artifactRepository = fakeArtifactRepository,
            settingsGradleProjectsProvider = projectsProvider,
            gradlePropertiesProvider = propertiesProvider,
        )

        val result = downloader.downloadAndInstallArtifacts(
            bomVersion = FAKE_BOM_VERSION,
            settingsGradleFile = Path(tempDir.absolutePath)
        )

        val downloadedArtifacts = fakeArtifactRepository.requestedArtifactFilesForDownload
            .map { (artifact, _) -> artifact }
            .distinct()
        assertEquals(
            listOf(
                Artifact(SQUARE_PROTOS_ARTIFACT_GROUP, "foo", "1.2.3", SQUARE_PUBLIC_REPO),
                Artifact(SQUARE_PROTOS_ARTIFACT_GROUP, "bar", "1.2.3", SQUARE_PUBLIC_REPO),
                Artifact(SQUARE_PROTOS_ARTIFACT_GROUP, "all-protos", "1.2.3", SQUARE_PUBLIC_REPO),
            ),
            downloadedArtifacts
        )
        assertEquals(SUCCESS, result.result)
        // Protos don't come from a BOM file
        assertEquals(totalProtosProjects, result.countArtifactsToDownload)
        assertEquals(
            totalProtosProjects * DownloadFileType.entries.size,
            result.countSuccessfulDownloadedArtifactFiles.toInt()
        )
        assertEquals(0, result.countFailedDownloadedArtifactFiles.toInt())
        assertEquals(
            FAKE_ARTIFACT_DOWNLOAD_SIZE_MB * totalProtosProjects * DownloadFileType.entries.size,
            result.totalDownloadSizeMb
        )
        assertEquals(totalProtosProjects, result.countSuccessfulInstalledArtifacts.toInt())
        assertEquals(0, result.countFailedInstalledArtifacts.toInt())

        // validate all durations are set to a non-negative value
        listOf(
            result.totalDurationMs,
            result.getArtifactsToDownloadDurationMs,
            result.p50DownloadTimeMs,
            result.p90DownloadTimeMs,
            result.p99DownloadTimeMs,
            result.maxDownloadTimeMs,
            result.p50InstallTimeMs,
            result.p90InstallTimeMs,
            result.p99InstallTimeMs,
            result.maxInstallTimeMs,
        ).forEach {
            assertNotEquals(-1, it)
        }
        assertEquals(1, fakeEventStream.receivedEvents.size)
    }
}
