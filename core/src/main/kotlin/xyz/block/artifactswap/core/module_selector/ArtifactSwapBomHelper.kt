package xyz.block.artifactswap.core.module_selector

import kotlin.jvm.java
import org.slf4j.LoggerFactory
import xyz.block.artifactswap.core.maven.Project
import xyz.block.artifactswap.core.network.ArtifactoryService

private val LOGGER = LoggerFactory.getLogger(ArtifactSwapBomHelper::class.java)

/** Interface for performing BOM operations for artifact swap. */
interface ArtifactSwapBomHelper {

  /** Determines the most recent BOM version relative to current git branch state. */
  suspend fun findBestBomVersion(): Result<String>

  /**
   * Load the BOM for the given version, will check local repository first before attempting to
   * fetch from artifactory.
   */
  suspend fun loadBom(bomVersion: String): Result<Project>
}

class RealArtifactSwapBomHelper(
  private val squareGit: SquareGit,
  private val localArtifactRepository: LocalArtifactRepository,
  private val artifactoryService: ArtifactoryService,
) : ArtifactSwapBomHelper {

  companion object {
    private const val ORIGIN_ARTIFACT_SYNC_GREEN_MAIN_BRANCH_NAME =
      "origin/artifact-sync-green-main"
    private const val BOM_ARTIFACT_NAME = "bom"
    private const val COUNT_SHARED_COMMITS_TO_CHECK_FOR_BOM = 5000
  }

  override suspend fun findBestBomVersion(): Result<String> = runCatching {
    val recentSharedCommits =
      squareGit.findRecentSharedCommits(
        baseBranch = ORIGIN_ARTIFACT_SYNC_GREEN_MAIN_BRANCH_NAME,
        count = COUNT_SHARED_COMMITS_TO_CHECK_FOR_BOM,
      )
        ?: throw IllegalStateException(
          "Unable to determine bom version, failed to fetch possible bom versions from git."
        )
    // keep both options here for now, we can remove the artifactory check once we are confident
    // that local approach works well
    val useArtifactory = false
    recentSharedCommits
      .firstOrNull { commit ->
        if (useArtifactory) {
          bomExistsInArtifactory(commit.name)
        } else {
          bomExistsLocally(commit.name)
        }
      }
      ?.name
      ?: throw IllegalStateException(
        "Traversed $COUNT_SHARED_COMMITS_TO_CHECK_FOR_BOM commits " +
          "from $ORIGIN_ARTIFACT_SYNC_GREEN_MAIN_BRANCH_NAME and found no BOMs in local m2."
      )
  }

  private suspend fun bomExistsLocally(string: String): Boolean {
    return localArtifactRepository.getInstalledBom(string).isSuccess
  }

  private suspend fun bomExistsInArtifactory(commitHash: String): Boolean {
    return try {
      artifactoryService.getPom(artifactName = BOM_ARTIFACT_NAME, version = commitHash)
      true
    } catch (e: Exception) {
      false
    }
  }

  override suspend fun loadBom(bomVersion: String): Result<Project> {
    return localArtifactRepository.getInstalledBom(bomVersion).recoverCatching {
      loadBomFromArtifactory(bomVersion)
    }
  }

  private suspend fun loadBomFromArtifactory(activeBomVersion: String): Project {
    return try {
      val project =
        artifactoryService.getPom(artifactName = BOM_ARTIFACT_NAME, version = activeBomVersion)
      LOGGER.debug("Loaded BOM from Artifactory for version: $activeBomVersion")
      project
    } catch (e: Exception) {
      LOGGER.warn("Unable to locate BOM version given: $activeBomVersion", e)
      throw e
    }
  }
}
