package xyz.block.artifactswap.core.module_selector

import java.nio.file.Path
import org.eclipse.jgit.lib.ObjectId
import xyz.block.artifactswap.core.shared_services.git.SquareGit

/** Fake implementation of SquareGit for testing. */
class FakeSquareGit : SquareGit {
  var recentCommits: List<ObjectId> = emptyList()
  var changedFiles: Set<Path> = emptySet()

  override suspend fun findRecentSharedCommits(baseRef: String, count: Int): List<ObjectId> {
    return recentCommits
  }

  override suspend fun findChangedFiles(baseRef: String): Result<Set<Path>> {
    return Result.success(changedFiles)
  }
}
