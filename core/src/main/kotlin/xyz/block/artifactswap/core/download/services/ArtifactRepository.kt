package xyz.block.artifactswap.core.download.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createParentDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.notExists
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okio.sink
import okio.use
import org.apache.logging.log4j.kotlin.logger
import xyz.block.artifactswap.core.config.ArtifactSwapConfig
import xyz.block.artifactswap.core.download.models.Artifact
import xyz.block.artifactswap.core.download.models.DownloadFileType
import xyz.block.artifactswap.core.download.models.DownloadedArtifactFileResult
import xyz.block.artifactswap.core.download.models.InstallArtifactFilesResult
import xyz.block.artifactswap.core.download.models.LocalArtifactState
import xyz.block.artifactswap.core.download.models.toArtifactoryUrl
import xyz.block.artifactswap.core.maven.Project
import xyz.block.artifactswap.core.network.ArtifactoryEndpoints

private const val BOM = "bom"
internal const val SQUARE_PUBLIC_REPO = "square-public-repo"

interface ArtifactRepository {
  suspend fun getInstalledBom(bomVersion: String): Result<Project>

  /** Determine the artifacts to , which includes a list of the most recent artifact coordinates */
  suspend fun getArtifactsInBom(bomVersion: String): Result<List<Artifact>>

  suspend fun downloadArtifactFile(
    artifact: Artifact,
    fileType: DownloadFileType,
  ): DownloadedArtifactFileResult

  fun getLocalArtifactState(artifact: Artifact, fileType: DownloadFileType): LocalArtifactState

  suspend fun installDownloadedArtifactFiles(
    downloadedArtifactFiles: List<DownloadedArtifactFileResult>
  ): InstallArtifactFilesResult
}

@OptIn(ExperimentalPathApi::class)
class RealArtifactRepository(
  private val localMavenPath: Path,
  private val artifactoryService: ArtifactoryEndpoints,
  private val ioDispatcher: CoroutineContext,
  private val objectMapper: ObjectMapper,
  private val config: ArtifactSwapConfig,
) : ArtifactRepository {

  companion object {
    private const val MAVEN_GROUP_SEPARATOR = "."
  }

  private val tempPathsToDelete = LinkedBlockingQueue<Path>()

  init {
    Runtime.getRuntime()
      .addShutdownHook(
        thread(start = false) {
          while (tempPathsToDelete.isNotEmpty()) {
            val path = tempPathsToDelete.remove()
            if (path.isDirectory()) {
              path.deleteRecursively()
            } else {
              path.deleteIfExists()
            }
          }
        }
      )
  }

  override suspend fun getInstalledBom(bomVersion: String): Result<Project> {
    val expectedBomFileName = "bom-$bomVersion.pom"
    // determine bom location from local maven repo + primary maven group
    var currentRepoLocation = localMavenPath
    config.primaryArtifactsMavenGroup.split(MAVEN_GROUP_SEPARATOR).forEach { group ->
      currentRepoLocation = currentRepoLocation.resolve(group)
    }
    // the bom is always in the "bom" folder for the maven group in a subdir of the version
    val expectedBomLocation =
      currentRepoLocation.resolve("bom").resolve(bomVersion).resolve(expectedBomFileName)

    return runCatching {
      return@runCatching withContext(ioDispatcher) {
        objectMapper.readValue<Project>(expectedBomLocation.inputStream())
      }
    }
  }

  override suspend fun getArtifactsInBom(bomVersion: String): Result<List<Artifact>> {
    return runCatching {
      val response =
        artifactoryService.getPom(
          repo = config.primaryRepositoryName,
          groupPath = config.primaryArtifactsMavenGroupArtifactoryPath,
          artifact = BOM,
          bomVersion,
        )

      val project =
        if (response.isSuccessful) {
          logger.debug { "Found BOM!" }
          requireNotNull(response.body()) { "Body of successful response for BOM was empty" }
        } else {
          logger.error { "Unable to locate BOM version given: $bomVersion" }
          throw Exception(
            "Got response: code - ${response.code()}, message - ${
            response.errorBody()
              ?.string() ?: response.message()
          }"
          )
        }
      val bomArtifact =
        Artifact(
          groupId = project.groupId,
          artifactId = project.artifactId,
          version = project.version,
          repo = config.primaryRepositoryName,
        )
      if (
        getLocalArtifactState(bomArtifact, DownloadFileType.POM) == LocalArtifactState.NOT_INSTALLED
      ) {
        // Write BOM to local file
        logger.debug { "Storing BOM locally" }
        withContext(ioDispatcher) {
          val bomFile = getExpectedLocalFilePath(bomArtifact, DownloadFileType.POM)
          bomFile.createParentDirectories()
          objectMapper.writeValue(bomFile.toFile(), project)
        }
      }
      requireNotNull(project.dependencyManagement) {
        "BOM should declare all artifacts in <dependencyManagement> section, but none was found " +
          "for BOM version $bomVersion."
      }
      project.artifactDependencies(config.primaryArtifactsMavenGroup).map { dependency ->
        Artifact(
          groupId = dependency.groupId,
          artifactId = dependency.artifactId,
          version = dependency.version,
          repo = config.primaryRepositoryName,
        )
      }
    }
  }

  override suspend fun downloadArtifactFile(
    artifact: Artifact,
    fileType: DownloadFileType,
  ): DownloadedArtifactFileResult {
    val downloadStartMs = Clock.systemUTC().millis()
    return runCatching {
        val fileResponse =
          artifactoryService.getFile(
            artifact.repo,
            artifact.groupId.replace(".", "/"),
            artifact.artifactId,
            artifact.version,
            fileType.pathSuffix,
          )
        if (fileResponse.isSuccessful) {
          logger.debug { "Downloaded artifact: $artifact, file Type: $fileType" }
          val body = requireNotNull(fileResponse.body()) { "File not found" }
          DownloadedArtifactFileResult.Success(
            artifact = artifact,
            downloadFileType = fileType,
            fileContents = body,
            fileContentsSizeBytes = body.contentLength(),
            downloadDurationMs = (Clock.systemUTC().millis() - downloadStartMs).milliseconds,
          )
        } else if (fileResponse.code().is4xx()) {
          DownloadedArtifactFileResult.NoFileExists(
            artifact = artifact,
            downloadFileType = fileType,
          )
        } else {
          throw Exception(
            """
                  Failed to download artifact from artifactory.
                  Artifact details: $artifact, file type: $fileType.
                  Url requested: ${
            artifact.toArtifactoryUrl(
              config.artifactoryBaseUrl,
              fileType,
            )
          }. 
                  Got response: code - ${fileResponse.code()}, message - ${
            fileResponse.errorBody()
              ?.string() ?: fileResponse.message()
          }
                """
              .trimIndent()
          )
        }
      }
      .getOrElse {
        DownloadedArtifactFileResult.Failure(
          artifact = artifact,
          downloadFileType = fileType,
          throwable = it,
          downloadDuration = (Clock.systemUTC().millis() - downloadStartMs).milliseconds,
        )
      }
  }

  override fun getLocalArtifactState(
    artifact: Artifact,
    fileType: DownloadFileType,
  ): LocalArtifactState {
    val file = getExpectedLocalFilePath(artifact, fileType)
    return if (file.fileOrArtifactDoesNotExist()) LocalArtifactState.NOT_INSTALLED
    else LocalArtifactState.INSTALLED
  }

  override suspend fun installDownloadedArtifactFiles(
    downloadedArtifactFiles: List<DownloadedArtifactFileResult>
  ): InstallArtifactFilesResult {
    val successfulDownloads =
      downloadedArtifactFiles.filterIsInstance<DownloadedArtifactFileResult.Success>()
    if (successfulDownloads.isEmpty()) {
      return InstallArtifactFilesResult.NoOp
    }
    logger.debug {
      "All files downloaded for artifact: ${downloadedArtifactFiles.first()}. " +
        "Installing to local maven repository."
    }
    val installStartTimeMs = Clock.systemUTC().millis()
    // Start a new coroutine and await for all units of work to complete
    return coroutineScope {
      val installResults =
        successfulDownloads.map { downloadedFile ->
          val installDestination =
            getExpectedLocalFilePath(downloadedFile.artifact, downloadedFile.downloadFileType)
          // Perform write operations asynchronously and on an IO dispatcher
          async(ioDispatcher) {
            // Wrap the result in a runCatching block to catch any exceptions
            runCatching {
              installDestination.createParentDirectories()
              downloadedFile.fileContents.use { body ->
                val tempPath = createTempFile(prefix = installDestination.name, suffix = ".tmp")
                tempPathsToDelete.add(tempPath)
                try {
                  // Write to the temp file (already created by createTempFile)
                  tempPath
                    .sink(StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
                    .use { sink -> body.source().readAll(sink) }
                  tempPath.moveTo(installDestination, overwrite = true)
                  // Successfully moved, remove from cleanup queue
                  tempPathsToDelete.remove(tempPath)
                } catch (e: Exception) {
                  // If anything fails, ensure temp file is cleaned up
                  tempPath.deleteIfExists()
                  tempPathsToDelete.remove(tempPath)
                  throw e
                }
              }
            }
          }
        }
      // Check if any of the install operations failed
      val completedInstallResults = installResults.awaitAll()
      return@coroutineScope if (completedInstallResults.all { result -> result.isSuccess }) {
        InstallArtifactFilesResult.Success(
          duration = (Clock.systemUTC().millis() - installStartTimeMs).milliseconds
        )
      } else {
        // Log the actual installation failures
        successfulDownloads.zip(completedInstallResults).forEach { (downloadedFile, installResult)
          ->
          installResult.exceptionOrNull()?.let { exception ->
            logger.error(exception) {
              "Failed to install file for artifact: ${downloadedFile.artifact}, " +
                "file type: ${downloadedFile.downloadFileType}"
            }
          }
        }
        InstallArtifactFilesResult.Failure(
          duration = (Clock.systemUTC().millis() - installStartTimeMs).milliseconds
        )
      }
    }
  }

  private fun getExpectedLocalFilePath(
    artifact: Artifact,
    downloadFileType: DownloadFileType,
  ): Path {
    val expectedLocalArtifactDirPath =
      localMavenPath
        .resolve(artifact.groupId.replace(".", File.separator))
        .resolve(artifact.artifactId)
        .resolve(artifact.version)

    val file =
      expectedLocalArtifactDirPath.resolve(
        "${artifact.artifactId}-${artifact.version}${downloadFileType.pathSuffix}"
      )
    return file
  }

  private fun Path.fileOrArtifactDoesNotExist(): Boolean {
    return when (extension) {
      "aar" -> {
        notExists() && parent.resolve("$nameWithoutExtension.jar").notExists()
      }

      "jar" -> {
        notExists() && parent.resolve("$nameWithoutExtension.aar").notExists()
      }

      else -> {
        notExists()
      }
    }
  }
}
