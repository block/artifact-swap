package xyz.block.artifactswap.core.module_selector

import java.nio.file.Path
import org.eclipse.jgit.lib.ObjectId

/** Fake implementation of SquareGit for testing. */
class FakeSquareGit : SquareGit {
  var recentCommits: List<ObjectId> = emptyList()
  var changedFiles: Set<Path> = emptySet()

  override suspend fun findRecentSharedCommits(baseBranch: String, count: Int): List<ObjectId> {
    return recentCommits
  }

  override suspend fun findChangedFiles(baseCommit: String): Result<Set<Path>> {
    return Result.success(changedFiles)
  }
}
