package xyz.block.artifactswap.core.module_selector

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.time.measureTimedValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import xyz.block.artifactswap.core.maven.Project

data class InstalledArtifact(val projectPath: String, val installedVersions: Set<String>)

/** Returns information about what artifacts are installed in m2 or a comparable local repo. */
interface LocalArtifactRepository {

  suspend fun getInstalledArtifacts(bom: Project): Result<Set<InstalledArtifact>>

  /** Returns the BOM Project installed locally. */
  suspend fun getInstalledBom(bomVersion: String): Result<Project>
}

/** Checks local .m2 cache for installed maven artifacts that match the artifact sync structure. */
class RealLocalArtifactRepository(
  private val xmlMapper: ObjectMapper,
  private val ioContext: CoroutineContext,
  private val mavenDirectory: Path = DEFAULT_LOCAL_MAVEN_DIRECTORY,
  private val logger: Logger? = null,
) : LocalArtifactRepository {

  companion object {
    private val DEFAULT_LOCAL_MAVEN_DIRECTORY: Path =
      Path(System.getProperty("user.home")).resolve(".m2")
  }

  /**
   * Checks local .m2 cache for installed maven artifacts that line up with what is expected from a
   * given bom version.
   *
   * Note, the bomVersion is used instead of checking all artifacts because the bom ensures that
   * individual artifacts are swappable while still working with the rest of the project. Without
   * enforcing that returned results are part of a single bom, there is a small risk that we would
   * swap an artifact for something and break an engineers build.
   */
  override suspend fun getInstalledArtifacts(bom: Project): Result<Set<InstalledArtifact>> =
    runCatching {
      coroutineScope {
        val (result, duration) =
          measureTimedValue {
            bom.dependencyManagement.dependencies.dependency
              .map { dependency ->
                async(ioContext) {
                  // no version present means we won't be able to find an artifact
                  val dependencyVersion = dependency.version ?: return@async null
                  val expectedProject =
                    mavenDirectory
                      .resolve("repository")
                      .resolve(dependency.groupId.replace('.', File.separatorChar))
                      .resolve(dependency.artifactId)
                      .resolve(dependencyVersion)

                  val sourcesJar =
                    expectedProject.resolve(
                      "${dependency.artifactId}-${dependencyVersion}-sources.jar"
                    )
                  val aar =
                    expectedProject.resolve("${dependency.artifactId}-${dependencyVersion}.aar")
                  val jar =
                    expectedProject.resolve("${dependency.artifactId}-${dependencyVersion}.jar")
                  val module =
                    expectedProject.resolve("${dependency.artifactId}-${dependencyVersion}.module")
                  val pom =
                    expectedProject.resolve("${dependency.artifactId}-${dependencyVersion}.pom")

                  val hasBinary = jar.exists() || aar.exists()
                  val hasMetaData = module.exists() || pom.exists()
                  val hasSources = sourcesJar.exists()

                  if (expectedProject.exists() && hasBinary && hasMetaData && hasSources) {
                    InstalledArtifact(
                      dependency.artifactId.artifactToProject(),
                      setOf(dependencyVersion),
                    )
                  } else {
                    null
                  }
                }
              }
              .awaitAll()
              .filterNotNull()
              .toSet()
          }
        logger?.debug("Found ${result.size} installed artifacts in $duration")
        result
      }
    }

  override suspend fun getInstalledBom(bomVersion: String): Result<Project> {
    val expectedBomFileName = "bom-$bomVersion.pom"
    val expectedBomLocation =
      mavenDirectory
        .resolve("repository")
        .resolve("com")
        .resolve("squareup")
        .resolve("register")
        .resolve("sandbags")
        .resolve("bom")
        .resolve(bomVersion)
        .resolve(expectedBomFileName)

    return runCatching {
      return@runCatching withContext(ioContext) {
        xmlMapper.readValue<Project>(expectedBomLocation.inputStream())
      }
    }
  }
}

internal fun String.artifactToProject(): String {
  return ":${replace('_', ':')}"
}
