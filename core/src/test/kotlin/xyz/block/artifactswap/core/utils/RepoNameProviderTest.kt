package xyz.block.artifactswap.core.utils

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories

class RepoNameProviderTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `repo name is correct when root dir is git repo`() {
        val expectedRepoName = "test-repo"
        val repoRoot = tempDir.resolve(expectedRepoName).also { repoRoot ->
            Git.init().setDirectory(repoRoot.toFile()).call().use { git ->
                git.remoteAdd()
                    .setName("origin")
                    .setUri(URIish("https://example.com/$expectedRepoName.git"))
                    .call();
            }

        }
        val repoNameProvider = RepoNameProvider(repoRoot)
        val actualRepoName = repoNameProvider.repoName
        assertEquals(expectedRepoName, actualRepoName)
    }

    @Test
    fun `repo name is correct when root dir is subdir of a git repo`() {
        val expectedRepoName = "test-repo"
        val repoRoot = tempDir.resolve(expectedRepoName).also { repoRoot ->
            Git.init().setDirectory(repoRoot.toFile()).call().use { git ->
                git.remoteAdd()
                    .setName("origin")
                    .setUri(URIish("https://example.com/$expectedRepoName.git"))
                    .call();
            }
        }
        val subDir = repoRoot.resolve("subdir").also { it.createDirectories() }
        val repoNameProvider = RepoNameProvider(subDir)
        val actualRepoName = repoNameProvider.repoName
        assertEquals(expectedRepoName, actualRepoName)
    }

    @Test
    fun `repo name is empty when no git repo is found`() {
        val expectedRepoName = ""
        val nonRepoRoot = tempDir.resolve("non-git-repo-directory")
        val repoNameProvider = RepoNameProvider(nonRepoRoot)
        val actualRepoName = repoNameProvider.repoName
        assertEquals(expectedRepoName, actualRepoName)
    }

    @Test
    fun `repo name is empty when repo has no remote named origin`() {
        val expectedRepoName = ""
        val repoRoot = tempDir.resolve(expectedRepoName).also { repoRoot ->
            Git.init().setDirectory(repoRoot.toFile()).call().use { git ->
                git.remoteAdd()
                    .setName("nonOrigin")
                    .setUri(URIish("https://example.com/$expectedRepoName.git"))
                    .call();
            }
        }
        val repoNameProvider = RepoNameProvider(repoRoot)
        val actualRepoName = repoNameProvider.repoName
        assertEquals(expectedRepoName, actualRepoName)
    }

}