package xyz.block.artifactswap.core.gradle

import org.apache.logging.log4j.kotlin.logger
import org.gradle.tooling.CancellationTokenSource
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.gradle.GradleBuild
import java.io.File
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.time.measureTimedValue

class GradleToolingHashingProjectsProvider(
    private val cancellationTokenSource: CancellationTokenSource,
    private val projectConnection: ProjectConnection,
    private val gradleArgs: List<String>,
    private val gradleJvmArgs: List<String>
): GradleProjectsProvider {

    companion object {
        private const val GRADLE_BUILD_FILE_NAME = "build.gradle"
        private const val SETTINGS_GRADLE_FILE_NAME = "settings.gradle"
        private val TYPES_TO_IGNORE = setOf(".txt", ".yaml", ".yml", ".md")
    }

    override suspend fun getProjectHashingInfos(): Result<List<ProjectHashingInfo>> {
        return runCatching {
            val (result, duration) = measureTimedValue {
                val buildModel = projectConnection.model(GradleBuild::class.java)
                    .withCancellationToken(cancellationTokenSource.token())
                    .addArguments(gradleArgs)
                    .addJvmArguments(gradleJvmArgs)
                    .get()

                buildModel.projects
                    .filter {
                        it.projectDirectory.resolve(GRADLE_BUILD_FILE_NAME).exists() &&
                                !it.projectDirectory.resolve(SETTINGS_GRADLE_FILE_NAME).exists()
                    }
                    .filterNot { it.path == ":" }
                    .map {
                        ProjectHashingInfo(
                            it.path,
                            it.projectDirectory.toPath(),
                            getFilesToHash(it.projectDirectory)
                        )
                    }
            }
            logger.info { "Fetching ${result.size} project hashing infos from Gradle took $duration" }
            result
        }
    }

    override suspend fun cleanup() {
        cancellationTokenSource.cancel()
        projectConnection.close()
    }

    private fun getFilesToHash(gradleProjectDirectory: File): Sequence<Path> {
        val buildDir = gradleProjectDirectory.toPath().resolve("build")
        val testDir = gradleProjectDirectory.toPath().resolve("src").resolve("test")
        val androidTestDir = gradleProjectDirectory.toPath().resolve("src").resolve("androidTest")
        return gradleProjectDirectory.walkTopDown()
            .map { it.toPath() }
            .filter { it.isRegularFile() }
            .filterNot {
                it.startsWith(buildDir) ||
                        it.startsWith(testDir) ||
                        it.startsWith(androidTestDir)
            }
            .filterNot { f -> TYPES_TO_IGNORE.any(f.pathString::endsWith) }
            .sorted()
    }

}
