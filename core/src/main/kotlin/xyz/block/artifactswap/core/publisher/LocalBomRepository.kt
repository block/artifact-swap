package xyz.block.artifactswap.core.publisher

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.apache.logging.log4j.kotlin.logger
import xyz.block.artifactswap.core.config.ArtifactSwapConfig
import xyz.block.artifactswap.core.maven.Metadata
import xyz.block.artifactswap.core.maven.Project

/** Implementation of BomRepository that uses local Maven repository as the backend. */
class LocalBomRepository(
  private val xmlMapper: ObjectMapper,
  private val config: ArtifactSwapConfig,
  private val mavenDirectory: Path =
    Path.of(config.mavenLocalDirectory.replace("\${user.home}", System.getProperty("user.home"))),
) : BomRepository {

  override suspend fun storeBom(project: Project, version: String): Result<Unit> {
    return runCatching {
      val localMavenDir =
        mavenDirectory
          .resolve(config.primaryArtifactsMavenGroup.replace('.', File.separatorChar))
          .resolve(project.artifactId)
          .resolve(version)

      localMavenDir.createDirectories()
      val pomFile = localMavenDir.resolve("${project.artifactId}-$version.pom")
      val pomContent = xmlMapper.writeValueAsString(project)
      pomFile.writeText(pomContent)
      logger.info { "BOM written to local Maven repository: $pomFile" }
    }
  }

  override suspend fun getBomMetadata(artifact: String): Result<Metadata?> {
    // Local Maven metadata is not used, always return null
    logger.debug { "Skipping metadata fetch for local repository (not needed)" }
    return Result.success(null)
  }

  override suspend fun storeBomMetadata(metadata: Metadata): Result<Unit> {
    // Local Maven metadata is not needed, no-op
    logger.debug { "Skipping metadata publish for local repository (not needed)" }
    return Result.success(Unit)
  }
}
