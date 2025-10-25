package xyz.block.artifactswap.core.download.services

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry.ChangeType.ADD
import org.eclipse.jgit.diff.DiffEntry.ChangeType.COPY
import org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE
import org.eclipse.jgit.diff.DiffEntry.ChangeType.MODIFY
import org.eclipse.jgit.diff.DiffEntry.ChangeType.RENAME
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.filter.RevFilter
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.absolute

/**
 * Helper class to access git information via JGit
 */
interface SquareGit {

  /**
   * Returns the commit hash of the youngest ancestor between HEAD and the base branch.
   */
  suspend fun findRecentSharedCommits(
    baseBranch: String,
    count: Int = 50,
  ): List<ObjectId>?

  /**
   * Returns the list of file paths (relative to the repository root) that have changed between the
   * head and the comparison commit
   *
   * Functionally similar to `git diff --name-only <COMMIT_HASH>`
   */
  suspend fun findChangedFiles(baseCommit: String): Result<Set<Path>>
}

private val LOGGER = LoggerFactory.getLogger(SquareGit::class.java)

class RealSquareGit(
  rootDir: Path,
  private val context: CoroutineContext,
) : SquareGit {

  private val repository = FileRepositoryBuilder()
    .readEnvironment()
    .findGitDir(rootDir.toFile())
    .build()

  private val repoRoot = repository.directory.toPath().parent

  private val git = Git(repository)

  private val headId = repository.resolve(Constants.HEAD)

  private val currentBranch: String
    get() = repository.branch ?: ""

  private fun getComparisonLabels(baseCommitHash: ObjectId): Set<String> {
    return git
      .nameRev()
      .addPrefix(Constants.R_HEADS)
      .add(baseCommitHash)
      .call()
      .asSequence()
      .map {
        LOGGER.debug("Found comparison label {}", it.value)
        return@map it.value
      }
      .filterNot {
        // Ignore relational branches (ex HEAD~1)
        it.contains("~") || it.contains("^")
      }
      .toSet()
  }

  override suspend fun findRecentSharedCommits(
    baseBranch: String,
    count: Int,
  ): List<ObjectId>? {
    val baseCommit = repository.resolve(baseBranch)
    val headCommit = repository.resolve(Constants.HEAD)
    val mergeBase = RevWalk(repository).use { walk ->
      try {
        val revCommit1 = walk.parseCommit(baseCommit)
        val revCommit2 = walk.parseCommit(headCommit)
        walk.findMergeBase(revCommit1, revCommit2)
      } catch (e: Exception) {
        LOGGER.error("Error finding recent shared commits", e)
        return null
      } finally {
        walk.dispose()
      }
    }
    if (mergeBase == null) {
      LOGGER.warn("No merge base found between $baseBranch and HEAD")
      return null
    }
    val commits = git.log()
      .add(mergeBase)
      .call()
      .take(count)
      .map { it.id }
    return commits
  }

  // Extension function to find the merge base
  fun RevWalk.findMergeBase(
    commit1: RevCommit,
    commit2: RevCommit
  ): RevCommit? {
    this.setRevFilter(RevFilter.MERGE_BASE)
    this.markStart(commit1)
    this.markStart(commit2)
    return this.next()  // This will give you the merge base
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override suspend fun findChangedFiles(baseBranch: String): Result<Set<Path>> = runCatching {
    val baseCommit = findRecentSharedCommits(baseBranch)?.first()
      ?: throw IllegalStateException("No recent shared commits found")
    LOGGER.debug("Got comparison labels: {}", getComparisonLabels(baseCommit))

    LOGGER.debug("Resolved headId={}", headId)

    LOGGER.debug("Resolved comparisonMergeBaseId={}", baseCommit)

    LOGGER.debug("Current branch={}", currentBranch)

    withContext(context) {
      val uncommittedChangesDeferred = async {
        findUncommittedChanges()
      }
      val committedChangesDeferred = async {
        findCommittedChanges(baseCommit)
      }
      (uncommittedChangesDeferred.await() + committedChangesDeferred.await())
        .map { repoRoot.resolve(it).absolute().normalize() }
        .toSet()
    }
  }

  private suspend fun findCommittedChanges(comparisonMergeBaseId: ObjectId): List<String> =
    withContext(context) {
      val diffs = repository.newObjectReader().use { reader ->

        val oldTreeItr = CanonicalTreeParser().apply { reset(reader, comparisonMergeBaseId.treeId) }
        val newTreeItr = CanonicalTreeParser().apply { reset(reader, headId.treeId) }

        return@use git.diff()
          .setOldTree(oldTreeItr)
          .setNewTree(newTreeItr)
          .call()
      }
      diffs.flatMap {
        when (it.changeType) {
          ADD, COPY, MODIFY -> listOf(it.newPath)
          RENAME -> listOf(it.oldPath, it.newPath)
          DELETE -> listOf(it.oldPath)
          null -> emptyList()
        }
      }
    }

  private suspend fun findUncommittedChanges(): List<String> = withContext(context) {
    ProcessBuilder("git", "diff", "--name-only")
      .start()
      .inputReader()
      .useLines { lines ->
        lines.toList()
      }
  }

  // Helper function to get the treeId of a commit object.
  private val ObjectId.treeId: ObjectId
    get() {
      return RevWalk(repository).use { walk ->
        val commit = walk.parseCommit(this)
        val tree = walk.parseTree(commit.tree.id)
        val id = tree.id
        walk.dispose()
        return@use id
      }
    }
}
