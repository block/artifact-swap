package xyz.block.artifactswap.core.module_selector

import java.nio.file.Path
import org.eclipse.jgit.lib.ObjectId
import xyz.block.artifactswap.core.shared_services.git.GitClient

/** Fake implementation of GitClient for testing. */
class FakeGitClient : GitClient {
  var recentCommits: List<ObjectId> = emptyList()
  var changedFiles: Set<Path> = emptySet()

  override suspend fun findRecentSharedCommits(baseRef: String, count: Int): List<ObjectId> {
    return recentCommits
  }

  override suspend fun findChangedFiles(baseRef: String): Result<Set<Path>> {
    return Result.success(changedFiles)
  }
}
