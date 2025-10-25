package xyz.block.artifactswap.core.download.services

import xyz.block.artifactswap.core.maven.Project
import xyz.block.artifactswap.core.network.ArtifactoryService

interface ArtifactSyncBomLoader {

  /**
   * Determines the most recent BOM version relative to current git branch state.
   */
  suspend fun findBestBomVersion(): Result<String>
}

class RealArtifactSyncBomLoader(
  private val squareGit: SquareGit,
  private val localArtifactRepository: ArtifactRepository,
  private val artifactoryService: ArtifactoryService,
) : ArtifactSyncBomLoader {

  companion object {
    private const val ORIGIN_GREEN_MASTER_BRANCH_NAME = "origin/artifact-sync-green-main"
    private const val BOM_ARTIFACT_NAME = "bom"
    private const val COUNT_SHARED_COMMITS_TO_CHECK_FOR_BOM = 5000
  }

  override suspend fun findBestBomVersion(): Result<String> = runCatching {
    val recentSharedCommits = squareGit.findRecentSharedCommits(
      baseBranch = ORIGIN_GREEN_MASTER_BRANCH_NAME,
      count = COUNT_SHARED_COMMITS_TO_CHECK_FOR_BOM
    )
      ?: throw IllegalStateException(
        "Unable to determine bom version, failed to fetch possible bom" +
          " versions from git."
      )

    // For each commit, check local first, then Artifactory
    // This ensures we find the most recent BOM available across both locations
    val bomCommit = recentSharedCommits.firstOrNull { commit ->
      bomExistsLocally(commit.name) || bomExistsInArtifactory(commit.name)
    }

    // If no BOMs found in either location
    return@runCatching bomCommit?.name
      ?: throw IllegalStateException(
        "Traversed $COUNT_SHARED_COMMITS_TO_CHECK_FOR_BOM commits " +
          "from $ORIGIN_GREEN_MASTER_BRANCH_NAME and found no BOMs in local m2 or Artifactory."
      )
  }

  private suspend fun bomExistsLocally(version: String): Boolean {
    return localArtifactRepository.getInstalledBom(version).isSuccess
  }

  private suspend fun bomExistsInArtifactory(version: String): Boolean {
    return runCatching {
      artifactoryService.getPom(BOM_ARTIFACT_NAME, version)
    }.isSuccess
  }
}
