package xyz.block.artifactswap.core.remover.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import xyz.block.artifactswap.core.maven.Project
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.kotlin.logger
import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.walk
import kotlin.time.Duration
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

data class InstalledBom(
  val version: String,
  val repositoryPath: Path,
  val installedProjects: List<InstalledProject>,
) {
  fun getArtifactsAndVersions(): Map<String, String> {
    return installedProjects.associate { it.projectPath to it.versions.first() }
  }
}


data class InstalledProject(
  val projectPath: String,
  val repositoryPath: Path,
  val versions: Set<String>
) {
  fun onlyVersions(versions: Set<String>): InstalledProject {
    return copy(versions = versions.toSet())
  }
}

/**
 * Returns information about what artifacts are installed in m2 or a comparable local repo.
 */
interface LocalArtifactRepository {

  fun getAllInstalledProjects(): Flow<InstalledProject>

  /**
   * Returns a list containing the most recent BOMs sorted by when they
   * were added (most recent first).
   */
  suspend fun getInstalledBomsByRecency(count: Int): List<InstalledBom>

  suspend fun deleteInstalledProjectVersions(installedProject: InstalledProject): List<String>

  suspend fun deleteInstalledBom(installedBom: InstalledBom): Boolean

  /**
   * Returns various metrics about the local repository, including:
   * - counts of projects that have at least one installed version
   * - counts of all installed artifacts
   * - size of all installed artifacts
   * - size of all installed boms
   * - overall size of all installed artifacts and boms
   * - durations for various portions of the operation
   */
  suspend fun measureRepository(): RepositoryStats?
}

data class RepositoryStats(
  val countInstalledProjects: Long = -1,
  val countInstalledArtifacts: Long = -1,
  val countInstalledBoms: Long = -1,
  val sizeOfInstalledArtifactsBytes: Long = -1,
  val sizeOfInstalledBomsBytes: Long = -1,
  val overallRepoSizeBytes: Long = -1,
  val installedArtifactsMeasurementDuration: Duration? = null,
  val installedBomsMeasurementDuration: Duration? = null,
  val measurementDuration: Duration? = null,
)

/**
 * Checks local .m2 cache for installed maven artifacts that match the artifact sync structure.
 */
class RealLocalArtifactRepository(
  private val localMavenDirectory: Path = Path.of(System.getProperty("user.home")).resolve(".m2"),
  private val xmlMapper: ObjectMapper,
  private val ioContext: CoroutineContext
) : LocalArtifactRepository {

  companion object {
    private const val BASE_GROUP_ID = "com.squareup.register.sandbags"
    private const val M2_REPOSITORY_DIR_NAME = "repository"
  }

  override suspend fun measureRepository(): RepositoryStats {
    var statsResult = RepositoryStats()
    val totalDuration = measureTime {
      withContext(ioContext) {
        val deferredInstalledArtifactsStats = async {
          logger.debug { "Measuring installed artifacts" }
          getInstalledArtifactsStats()
        }
        val deferredInstalledBomStats = async {
          logger.debug { "Measuring installed boms" }
          getInstalledBomsStats()
        }
        val installedArtifactsStats = deferredInstalledArtifactsStats.await()
        logger.debug { "Installed artifacts stats: $installedArtifactsStats "}
        statsResult = statsResult.copy(
          countInstalledProjects = installedArtifactsStats.countInstalledProjects,
          countInstalledArtifacts = installedArtifactsStats.countInstalledArtifacts,
          sizeOfInstalledArtifactsBytes = installedArtifactsStats.sizeOfInstalledArtifactsBytes,
          installedArtifactsMeasurementDuration = installedArtifactsStats.installedArtifactsMeasurementDuration
        )
        val installedBomStats = deferredInstalledBomStats.await()
        logger.debug { "Installed boms stats: $installedBomStats" }
        statsResult = statsResult.copy(
          countInstalledBoms = installedBomStats.countInstalledBoms,
          sizeOfInstalledBomsBytes = installedBomStats.sizeOfInstalledBomsBytes,
          installedBomsMeasurementDuration = installedBomStats.installedBomsMeasurementDuration
        )
      }
    }
    return statsResult.copy(
      overallRepoSizeBytes = statsResult.sizeOfInstalledArtifactsBytes + statsResult.sizeOfInstalledBomsBytes,
      measurementDuration = totalDuration,
    )
  }

  private suspend fun getInstalledBomsStats(): RepositoryStats {
    val (bomStats, bomStatsDuration) = measureTimedValue {
      val bomDirectory = localMavenDirectory.resolve(M2_REPOSITORY_DIR_NAME)
        .resolve(BASE_GROUP_ID.replace('.', File.separatorChar))
        .resolve("bom")
      // hitting file system potentially many times (100s or 1000s expected)
      // ensure running in background context
      withContext(ioContext) {
        val bomVersionDirectories = bomDirectory.listDirectoryEntries().filter { it.isDirectory() }
        RepositoryStats(
          countInstalledBoms = bomVersionDirectories.count().toLong(),
          sizeOfInstalledBomsBytes = bomVersionDirectories.sumOf { it.sumFileSizes() }
        )
      }
    }
    return bomStats.copy(
      installedBomsMeasurementDuration = bomStatsDuration
    )
  }

  private suspend fun getInstalledArtifactsStats(): RepositoryStats {
    val (installedArtifactsStats, installedArtifactsStatsDuration) = measureTimedValue {
      getAllInstalledProjects()
        .map { installedProject ->
          InstalledProjectStats(
            countInstalledArtifacts = installedProject.versions.size.toLong(),
            sizeOfInstalledArtifactsBytes = installedProject.versions.sumOf { version ->
              val versionDirectory = installedProject.repositoryPath.resolve(version)
              versionDirectory.sumFileSizes()
            }
          )
        }
        .fold(RepositoryStats(), { acc, installedProjectStats ->
          acc.copy(
            countInstalledProjects = acc.countInstalledProjects + 1,
            countInstalledArtifacts = acc.countInstalledArtifacts + installedProjectStats.countInstalledArtifacts,
            sizeOfInstalledArtifactsBytes = acc.sizeOfInstalledArtifactsBytes + installedProjectStats.sizeOfInstalledArtifactsBytes
          )
        })
    }
    return installedArtifactsStats.copy(
      installedArtifactsMeasurementDuration = installedArtifactsStatsDuration
    )
  }

  data class InstalledProjectStats(
    val countInstalledArtifacts: Long = 0,
    val sizeOfInstalledArtifactsBytes: Long = 0
  )

  /**
   * Locate the bom directory, which contains entries for each bom version (names of the entries are md5 hashes).
   * Scan the bom directory and determine the top N that were most recently added/modified, then read and return
   * the information in the bom file.
   */
  override suspend fun getInstalledBomsByRecency(count: Int): List<InstalledBom> {
    return withContext(ioContext) {
      val baseGroupDir = localMavenDirectory.resolve(M2_REPOSITORY_DIR_NAME)
        .resolve(BASE_GROUP_ID.replace('.', File.separatorChar))
      val bomDirectory = baseGroupDir
        .resolve("bom")
      val bomVersionDirectories = bomDirectory.listDirectoryEntries().filter { it.isDirectory() }
      bomVersionDirectories
        .map {
          async {
            // getLastModifiedTime will ask the os to query the file system, which can hit a block device,
            // so run this in coroutine on io thread
            it to it.getLastModifiedTime()
          }
        }.awaitAll()
        .sortedByDescending { it.second.toMillis() }
        .map { it.first }
        .take(count)
        .mapNotNull { bomVersionDirectory ->
          val bomVersion = bomVersionDirectory.name
          val bomFileName = "bom-$bomVersion.pom"
          val bomFile = bomVersionDirectory.resolve(bomFileName)
          if (bomFile.exists()) {
            // read bom files in parallel on io thread
            async {
              InstalledBom(
                version = bomVersion,
                repositoryPath = bomFile,
                installedProjects = bomFile.inputStream()
                  .use { xmlMapper.readValue<Project>(it) }
                  .toInstalledProjects(baseGroupDir),
              )
            }
          } else {
            null
          }
        }.awaitAll()
    }
  }

  @OptIn(ExperimentalPathApi::class)
  override suspend fun deleteInstalledBom(installedBom: InstalledBom) = withContext(ioContext) {
    // running on io dispatcher due to file system operation that touches block device
    try {
      // delete parent directory containing bom so it is not dangling
      installedBom.repositoryPath.parent.deleteRecursively()
      true
    } catch (ioException: IOException) {
      logger.error(ioException) {
        "Failed to delete bom directory: ${installedBom.repositoryPath.parent}"
      }
      false
    }
  }

  override fun getAllInstalledProjects(): Flow<InstalledProject> =
    channelFlow {
      val bomDirectoryName = "bom"
      val moduleArtifactDirectories = localMavenDirectory.resolve(M2_REPOSITORY_DIR_NAME)
        .resolve(BASE_GROUP_ID.replace('.', File.separatorChar))
        .listDirectoryEntries()
        .filter { it.isDirectory() && it.name != bomDirectoryName }
      val count = moduleArtifactDirectories.count()
      logger.debug { "Found $count installed projects" }
      launch {
        moduleArtifactDirectories.forEach { moduleDirectory ->
          val artifactVersionDirectories = moduleDirectory.listDirectoryEntries().filter { it.isDirectory() }
          send(
            InstalledProject(
              projectPath = moduleDirectory.name
                .split("_")
                .joinToString(":", prefix = ":"),
              repositoryPath = moduleDirectory,
              versions = artifactVersionDirectories.map { it.name }.toSet()
            )
          )
        }
      }
    }.flowOn(ioContext)

  override suspend fun deleteInstalledProjectVersions(installedProject: InstalledProject): List<String> {
    val artifactDirectory = installedProject.repositoryPath
    val deletedVersions = withContext(ioContext) {
      installedProject.versions.map { version ->
        val versionDirectory = artifactDirectory.resolve(version)
        async {
          if (versionDirectory.exists() && versionDirectory.toFile().deleteRecursively()) {
            version
          } else {
            null
          }
        }
      }.awaitAll().filterNotNull()
    }
    return deletedVersions
  }
}

private fun Project.toInstalledProjects(baseGroupDir: Path): List<InstalledProject> {
  return dependencyManagement.dependencies.dependency.map {
    val projectRepositoryPath = baseGroupDir
      .resolve(artifactId)
    InstalledProject(
      projectPath = it.artifactId.artifactIdToProjectPath(),
      repositoryPath = projectRepositoryPath,
      versions = setOf(it.version)
    )
  }
}

@OptIn(ExperimentalPathApi::class)
private fun Path.sumFileSizes(): Long {
  if (isRegularFile()) {
    return fileSize()
  }
  return walk()
    .filter { it.isRegularFile() }
    .sumOf { it.fileSize() }
}

fun String.artifactIdToProjectPath(): String {
  return ":${replace('_', ':')}"
}
