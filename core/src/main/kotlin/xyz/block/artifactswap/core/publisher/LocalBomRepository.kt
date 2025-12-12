package xyz.block.artifactswap.core.publisher

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlinx.coroutines.flow.toList
import org.apache.logging.log4j.kotlin.logger
import xyz.block.artifactswap.core.config.ArtifactSwapConfig
import xyz.block.artifactswap.core.maven.Dependency
import xyz.block.artifactswap.core.maven.Metadata
import xyz.block.artifactswap.core.maven.Project
import xyz.block.artifactswap.core.repository.LocalArtifactRepository

/** Implementation of BomRepository that uses local Maven repository as the backend. */
class LocalBomRepository(
  private val localArtifactRepository: LocalArtifactRepository,
  private val xmlMapper: ObjectMapper,
  private val config: ArtifactSwapConfig,
  private val mavenDirectory: Path =
    Path.of(System.getProperty("user.home")).resolve(".m2/repository"),
) : BomRepository {
  override suspend fun fetchAvailableDependencies(
    projectHashes: Map<String, String>
  ): List<Dependency> {
    // Get all installed projects from local maven
    val installedProjects =
      localArtifactRepository.getAllInstalledProjects().toList().associateBy { it.projectPath }

    return projectHashes.mapNotNull { (artifact, version) ->
      // Convert artifact ID to project path format (e.g., "module_submodule" ->
      // ":module:submodule")
      val projectPath = ":${artifact.replace('_', ':')}"
      val installedProject = installedProjects[projectPath]

      if (installedProject != null && version in installedProject.versions) {
        Dependency(
          groupId = config.primaryArtifactsMavenGroup,
          artifactId = artifact,
          version = version,
        )
      } else {
        if (installedProject == null) {
          logger.warn { "Project $projectPath not found in local Maven repository" }
        } else {
          logger.warn { "Version $version of $projectPath not found in local Maven repository" }
        }
        null
      }
    }
  }

  override suspend fun publishBom(project: Project, version: String): Result<Unit> {
    return runCatching {
      val localMavenDir =
        mavenDirectory
          .resolve(config.primaryArtifactsMavenGroup.replace('.', File.separatorChar))
          .resolve(project.artifactId)
          .resolve(version)

      localMavenDir.createDirectories()
      val pomFile = localMavenDir.resolve("${project.artifactId}-$version.pom")
      val pomContent = xmlMapper.writeValueAsString(project)
      pomFile.writeText(pomContent)
      logger.info { "BOM written to local Maven repository: $pomFile" }
    }
  }

  override suspend fun fetchBomMetadata(artifact: String): Result<Metadata?> {
    // Local Maven metadata is not used, always return null
    logger.debug { "Skipping metadata fetch for local repository (not needed)" }
    return Result.success(null)
  }

  override suspend fun publishBomMetadata(metadata: Metadata): Result<Unit> {
    // Local Maven metadata is not needed, no-op
    logger.debug { "Skipping metadata publish for local repository (not needed)" }
    return Result.success(Unit)
  }
}
