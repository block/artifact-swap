package xyz.block.artifactswap.core.download

import kotlin.time.Duration.Companion.milliseconds
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.ByteString.Companion.encodeUtf8
import xyz.block.artifactswap.core.download.models.Artifact
import xyz.block.artifactswap.core.download.models.DownloadFileType
import xyz.block.artifactswap.core.download.models.DownloadedArtifactFileResult
import xyz.block.artifactswap.core.download.models.InstallArtifactFilesResult
import xyz.block.artifactswap.core.download.models.InstallArtifactFilesResult.Success
import xyz.block.artifactswap.core.download.models.LocalArtifactState
import xyz.block.artifactswap.core.download.services.ArtifactRepository
import xyz.block.artifactswap.core.maven.Project

val FAKE_SUCCESS_DURATION = 200.milliseconds
val FAKE_FAILURE_DURATION = 100.milliseconds

const val FAKE_ARTIFACT_DOWNLOAD_SIZE_BYTES = 1024 * 1024 * 5L
const val FAKE_ARTIFACT_DOWNLOAD_SIZE_MB = FAKE_ARTIFACT_DOWNLOAD_SIZE_BYTES / (1024 * 1024)

class FakeArtifactRepository(
  var getBomResult: Result<List<Artifact>> = Result.success(emptyList())
) : ArtifactRepository {

  val requestedArtifactFilesForDownload = mutableSetOf<Pair<Artifact, DownloadFileType>>()
  val artifactsToFailDownload = mutableSetOf<Artifact>()

  fun markArtifactForDownloadFailure(artifact: Artifact) {
    artifactsToFailDownload.add(artifact)
  }

  override suspend fun getInstalledBom(bomVersion: String): Result<Project> {
    TODO("Not yet implemented")
  }

  override suspend fun getArtifactsInBom(bomVersion: String): Result<List<Artifact>> {
    return getBomResult
  }

  override suspend fun downloadArtifactFile(
    artifact: Artifact,
    fileType: DownloadFileType,
  ): DownloadedArtifactFileResult {
    requestedArtifactFilesForDownload.add(artifact to fileType)
    if (artifactsToFailDownload.contains(artifact)) {
      return DownloadedArtifactFileResult.Failure(
        artifact,
        fileType,
        Exception("Failed to download artifact"),
        downloadDuration = FAKE_FAILURE_DURATION,
      )
    }
    val body =
      "Fake content for ${artifact.artifactId}-${artifact.version}${fileType.pathSuffix}"
        .encodeUtf8()
        .toResponseBody(null)
    return DownloadedArtifactFileResult.Success(
      artifact = artifact,
      downloadFileType = fileType,
      fileContents = body,
      fileContentsSizeBytes = FAKE_ARTIFACT_DOWNLOAD_SIZE_BYTES,
      downloadDurationMs = FAKE_SUCCESS_DURATION,
    )
  }

  val localArtifactStateCache = mutableMapOf<Pair<Artifact, DownloadFileType>, LocalArtifactState>()

  fun setLocalArtifactState(
    artifact: Artifact,
    fileType: DownloadFileType,
    state: LocalArtifactState,
  ) {
    localArtifactStateCache[artifact to fileType] = state
  }

  override fun getLocalArtifactState(
    artifact: Artifact,
    fileType: DownloadFileType,
  ): LocalArtifactState {
    return localArtifactStateCache.getOrDefault(
      artifact to fileType,
      LocalArtifactState.NOT_INSTALLED,
    )
  }

  val installArtifactResultsCache: MutableMap<Artifact, InstallArtifactFilesResult> = mutableMapOf()

  fun setInstallArtifactResult(artifact: Artifact, result: InstallArtifactFilesResult) {
    installArtifactResultsCache[artifact] = result
  }

  override suspend fun installDownloadedArtifactFiles(
    downloadedArtifactFiles: List<DownloadedArtifactFileResult>
  ): InstallArtifactFilesResult {
    val artifacts =
      downloadedArtifactFiles.mapNotNull { (it as? DownloadedArtifactFileResult.Success)?.artifact }
    if (artifacts.isEmpty()) {
      return InstallArtifactFilesResult.NoOp
    }
    return artifacts
      .map { artifact ->
        installArtifactResultsCache.getOrDefault(artifact, Success(FAKE_SUCCESS_DURATION))
      }
      .firstOrNull { it is InstallArtifactFilesResult.Failure } ?: Success(FAKE_SUCCESS_DURATION)
  }
}
