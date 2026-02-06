package xyz.block.artifactswap.core.publisher

import kotlinx.coroutines.flow.toList
import org.apache.logging.log4j.kotlin.logger
import xyz.block.artifactswap.core.config.ArtifactSwapConfig
import xyz.block.artifactswap.core.maven.Dependency
import xyz.block.artifactswap.core.repository.LocalArtifactRepository

/** Implementation of [PublishedArtifactRepository] that uses the local Maven repository. */
class LocalPublishedArtifactRepository(
  private val localArtifactRepository: LocalArtifactRepository,
  private val config: ArtifactSwapConfig,
) : PublishedArtifactRepository {
  override suspend fun getAvailableDependencies(
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
}
