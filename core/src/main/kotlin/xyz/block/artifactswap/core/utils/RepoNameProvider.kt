package xyz.block.artifactswap.core.utils

import org.apache.logging.log4j.kotlin.logger
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path

internal class RepoNameProvider(private val rootDir: Path) {

    companion object {
        val default: RepoNameProvider by lazy { RepoNameProvider(Path.of("")) }
    }

    val repoName: String by lazy {
        try {
            val repoRoot = findRepoRoot() ?: return@lazy ""
            val repoName = FileRepositoryBuilder()
                .readEnvironment()
                .findGitDir(repoRoot.toFile())
                .build().use { repo ->
                val originUrl = repo.remoteNames
                    .filter { it == "origin" }
                    .map { repo.getConfig().getString("remote", it, "url") }
                    .firstOrNull() ?: return@lazy ""
                originUrl.substringAfterLast("/").substringBeforeLast(".")
            }
            logger.debug("Repo name: $repoName")
            repoName
        } catch (e: IOException) {
            logger.warn("Failed to get repo name", e)
            ""
        }
    }

    /**
     * Find location in current directory/parents that has a .git folder, if it exists
     */
    private fun findRepoRoot(): Path? {
        val currentDir = rootDir.toAbsolutePath()
        var dir = currentDir
        while (dir.parent != null) {
            if (dir.resolve(".git").toFile().exists()) {
                return dir
            }
            dir = dir.parent
        }
        return null
    }
}