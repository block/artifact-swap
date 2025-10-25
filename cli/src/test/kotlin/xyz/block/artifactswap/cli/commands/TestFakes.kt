package xyz.block.artifactswap.cli.commands

import xyz.block.artifactswap.core.gradle.GradlePropertiesProvider
import xyz.block.artifactswap.core.maven.Project
import xyz.block.artifactswap.core.gradle.GradleProjectsProvider
import xyz.block.artifactswap.core.gradle.ProjectHashingInfo
import okhttp3.ResponseBody
import okio.ByteString.Companion.encodeUtf8
import xyz.block.artifactswap.core.download.models.Artifact
import xyz.block.artifactswap.core.download.models.ArtifactDownloaderEvent
import xyz.block.artifactswap.core.download.models.DownloadFileType
import xyz.block.artifactswap.core.download.models.DownloadedArtifactFileResult
import xyz.block.artifactswap.core.download.models.InstallArtifactFilesResult
import xyz.block.artifactswap.core.download.models.LocalArtifactState
import xyz.block.artifactswap.core.download.services.ArtifactDownloaderEventStream
import xyz.block.artifactswap.core.download.services.ArtifactRepository
import kotlin.time.Duration.Companion.milliseconds

internal val FAKE_SUCCESS_DURATION = 200.milliseconds

class FakeEventStream : ArtifactDownloaderEventStream {
    val receivedEvents = mutableListOf<ArtifactDownloaderEvent>()

    override suspend fun sendEvents(events: List<ArtifactDownloaderEvent>): Boolean {
        receivedEvents.addAll(events)
        return true
    }
}

class FakeGradlePropertiesProvider : GradlePropertiesProvider {
    override fun get(key: String): String {
        return "1.2.3"
    }
}

class FakeGradleProjectsProvider(
    projects: List<ProjectHashingInfo> = emptyList()
) : GradleProjectsProvider {
    var projectHashingInfos: Result<List<ProjectHashingInfo>> = Result.success(projects)
    var cleanupCalled = false

    override suspend fun getProjectHashingInfos(): Result<List<ProjectHashingInfo>> {
        return projectHashingInfos
    }

    override suspend fun cleanup() {
        cleanupCalled = true
    }
}

class FakeArtifactRepository(
    var getBomResult: Result<List<Artifact>> = Result.success(emptyList()),
) : ArtifactRepository {

    override val baseArtifactoryUrl = "http://fake.artifactory.url"

    override suspend fun getInstalledBom(bomVersion: String): Result<Project> {
        TODO("Not yet implemented")
    }

    override suspend fun getArtifactsInBom(bomVersion: String): Result<List<Artifact>> {
        return getBomResult
    }

    override suspend fun downloadArtifactFile(
        artifact: Artifact,
        fileType: DownloadFileType
    ): DownloadedArtifactFileResult {
        val body = ResponseBody.create(
            null,
            "Fake content for ${artifact.artifactId}-${artifact.version}${fileType.pathSuffix}".encodeUtf8()
        )
        return DownloadedArtifactFileResult.Success(
            artifact = artifact,
            downloadFileType = fileType,
            fileContents = body,
            fileContentsSizeBytes = 1024L,
            downloadDurationMs = FAKE_SUCCESS_DURATION
        )
    }

    override fun getLocalArtifactState(
        artifact: Artifact,
        fileType: DownloadFileType
    ): LocalArtifactState {
        // Default to not installed so artifacts will be downloaded in tests
        return LocalArtifactState.NOT_INSTALLED
    }

    override suspend fun installDownloadedArtifactFiles(
        downloadedArtifactFiles: List<DownloadedArtifactFileResult>
    ): InstallArtifactFilesResult {
        val artifacts = downloadedArtifactFiles.mapNotNull { (it as? DownloadedArtifactFileResult.Success)?.artifact }
        if (artifacts.isEmpty()) {
            return InstallArtifactFilesResult.NoOp
        }
        return InstallArtifactFilesResult.Success(FAKE_SUCCESS_DURATION)
    }
}
