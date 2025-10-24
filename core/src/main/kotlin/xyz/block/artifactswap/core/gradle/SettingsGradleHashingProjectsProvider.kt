package xyz.block.artifactswap.core.gradle

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.kotlin.logger
import xyz.block.artifactswap.core.config.ArtifactSwapConfigHolder
import java.io.File
import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.time.measureTimedValue

/**
 * Determines which Gradle projects should participate in the Artifact Sync run based
 * on parsing the `settings_modules_all.gradle` file in the root of the repo.
 */
class SettingsGradleHashingProjectsProvider(
    private val applicationDirectory: Path,
    private val settingsFile: Path,
    private val coroutineDispatcher: CoroutineDispatcher
) : GradleProjectsProvider {

    companion object {
        val GRADLE_INCLUDE_PROJECT = Regex("include *[( ]['\"](.+)['\"][) ]?")
        val GRADLE_EXCLUDE_PROJECTS = ArtifactSwapConfigHolder.instance.excludeGradleProjects
    }

    override suspend fun getProjectHashingInfos(): Result<List<ProjectHashingInfo>> =
        withContext(coroutineDispatcher) {
            runCatching {
                val (result, duration) = measureTimedValue {
                    settingsFile
                        .readLines()
                        .filterNot { it.trim().startsWith("//") } // remove comments
                        .mapNotNull { GRADLE_INCLUDE_PROJECT.find(it)?.groupValues?.get(1) }
                        .map { projectGradlePath ->
                            val projectRelativePath =
                                projectGradlePath.replace(":", File.separator).removePrefix(File.separator)
                            ProjectHashingInfo(
                                projectPath = projectGradlePath,
                                projectDirectory = applicationDirectory.resolve(projectRelativePath),
                                filesToHash = sequence {
                                    throw UnsupportedOperationException(
                                        "Hashing files is unsupported with SettingsGradleHashingProjectsProvider, you should only hash files in CI using GradleToolingHashingProjectsProvider."
                                    )
                                }
                            )
                        }
                        .filterNot { it.projectPath in GRADLE_EXCLUDE_PROJECTS }
                }
                logger.debug {
                    "Took ${duration.inWholeMilliseconds}ms to find ${result.size} project hashing infos by parsing $settingsFile"
                }
                result
            }
        }

    override suspend fun cleanup() {
        // no need
    }
}
