package xyz.block.artifactswap.core.download.services

import org.slf4j.LoggerFactory
import xyz.block.artifactswap.core.config.ArtifactSwapConfig
import xyz.block.artifactswap.core.maven.Project
import xyz.block.artifactswap.core.network.ArtifactoryService

private val LOGGER = LoggerFactory.getLogger(ArtifactSyncBomLoader::class.java)

/** Interface for performing BOM operations for artifact swap. */
interface ArtifactSyncBomLoader {

  /**
   * Determines the most recent BOM version relative to current git branch state.
   *
   * @param checkRemote If true, checks both local and remote (Artifactory) for BOMs. If false, only
   *   checks local repository.
   */
  suspend fun findBestBomVersion(checkRemote: Boolean = false): Result<String>

  /**
   * Load the BOM for the given version, will check local repository first before attempting to
   * fetch from artifactory.
   */
  suspend fun loadBom(bomVersion: String): Result<Project>
}

class RealArtifactSyncBomLoader(
  private val squareGit: SquareGit,
  private val localArtifactRepository: ArtifactRepository,
  private val artifactoryService: ArtifactoryService,
  private val config: ArtifactSwapConfig,
) : ArtifactSyncBomLoader {

  companion object {
    private const val ORIGIN_ARTIFACT_SYNC_GREEN_MAIN_BRANCH_NAME =
      "origin/artifact-sync-green-main"
    private const val BOM_ARTIFACT_NAME = "bom"
    private const val COUNT_SHARED_COMMITS_TO_CHECK_FOR_BOM = 5000
    private val LOGGER = LoggerFactory.getLogger(RealArtifactSyncBomLoader::class.java)
  }

  override suspend fun findBestBomVersion(checkRemote: Boolean): Result<String> = runCatching {
    val recentSharedCommits =
      squareGit.findRecentSharedCommits(
        baseRef = config.bomSourceBranchName,
        count = COUNT_SHARED_COMMITS_TO_CHECK_FOR_BOM,
      )
        ?: throw IllegalStateException(
          "Unable to determine bom version, failed to fetch possible bom versions from git."
        )

    val matchingBom =
      recentSharedCommits.firstOrNull { commit ->
        bomExistsLocally(commit.name) || (checkRemote && bomExistsInArtifactory(commit.name))
      }
        ?: throw IllegalStateException(
          "Traversed $COUNT_SHARED_COMMITS_TO_CHECK_FOR_BOM commits " +
            "from ${config.bomSourceBranchName} and found no matching BOMs"
        )
    matchingBom.name
  }

  override suspend fun loadBom(bomVersion: String): Result<Project> {
    return localArtifactRepository.getInstalledBom(bomVersion).recoverCatching {
      loadBomFromArtifactory(bomVersion)
    }
  }

  private suspend fun bomExistsLocally(version: String): Boolean {
    return localArtifactRepository.getInstalledBom(version).isSuccess
  }

  private suspend fun bomExistsInArtifactory(version: String): Boolean {
    return runCatching { artifactoryService.getPom(BOM_ARTIFACT_NAME, version) }.isSuccess
  }

  private suspend fun loadBomFromArtifactory(version: String): Project {
    return try {
      val project = artifactoryService.getPom(artifactName = BOM_ARTIFACT_NAME, version = version)
      LOGGER.debug("Loaded BOM from Artifactory for version: $version")
      project
    } catch (e: Exception) {
      LOGGER.warn("Unable to locate BOM version given: $version", e)
      throw e
    }
  }
}
