package xyz.block.artifactswap.core.shared_services.git

import java.nio.file.Path
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.io.path.absolute
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RealSquareGitTest {

  /** Test repo created in system temp dir so it's outside any existing git repo. */
  private lateinit var repoDir: Path
  private lateinit var remoteDir: Path

  @BeforeEach
  fun setup() {
    repoDir = createTempDirectory("square-git-test-repo-")
    remoteDir = createTempDirectory("square-git-test-remote-")

    git("init", "-b", "main")
    git("config", "user.name", "Test")
    git("config", "user.email", "test@test.com")

    repoDir.resolve("tracked.txt").writeText("initial content")
    git("add", ".")
    git("commit", "-m", "Initial commit")

    // Set up a bare remote and push so origin/main exists for merge base resolution
    ProcessBuilder("git", "init", "--bare", remoteDir.toString())
      .redirectErrorStream(true)
      .start()
      .waitFor()
    git("remote", "add", "origin", remoteDir.toString())
    git("push", "-u", "origin", "main")
  }

  @AfterEach
  fun cleanup() {
    repoDir.toFile().deleteRecursively()
    remoteDir.toFile().deleteRecursively()
  }

  private fun newSquareGit(): RealSquareGit = RealSquareGit(repoDir, EmptyCoroutineContext)

  @Test
  fun `findChangedFiles detects untracked new files`() = runTest {
    repoDir.resolve("subdir").toFile().mkdirs()
    repoDir.resolve("subdir/new-file.txt").writeText("new content")

    assertEquals(setOf("subdir/new-file.txt"), findChangedRelativePaths())
  }

  @Test
  fun `findChangedFiles detects modified tracked files`() = runTest {
    repoDir.resolve("tracked.txt").writeText("modified content")

    assertEquals(setOf("tracked.txt"), findChangedRelativePaths())
  }

  @Test
  fun `findChangedFiles detects staged new files`() = runTest {
    repoDir.resolve("staged.txt").writeText("staged content")
    git("add", "staged.txt")

    assertEquals(setOf("staged.txt"), findChangedRelativePaths())
  }

  @Test
  fun `findChangedFiles detects committed changes since base`() = runTest {
    repoDir.resolve("committed.txt").writeText("committed content")
    git("add", ".")
    git("commit", "-m", "Add committed file")

    // Construct after the commit so JGit's cached headId reflects the new commit
    assertEquals(setOf("committed.txt"), findChangedRelativePaths())
  }

  @Test
  fun `findChangedFiles returns empty when no changes`() = runTest {
    assertEquals(emptySet(), findChangedRelativePaths())
  }

  private suspend fun findChangedRelativePaths(): Set<String> {
    val root = repoDir.absolute().normalize()
    return newSquareGit()
      .findChangedFiles(baseRef = "origin/main")
      .getOrThrow()
      .map { root.relativize(it).toString() }
      .toSet()
  }

  private fun git(vararg args: String) {
    val command = listOf("git") + args.toList()
    val process =
      ProcessBuilder(command).directory(repoDir.toFile()).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
      throw RuntimeException("git ${args.joinToString(" ")} failed:\n$output")
    }
  }
}
