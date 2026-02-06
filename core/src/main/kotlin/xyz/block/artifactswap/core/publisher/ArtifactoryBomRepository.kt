package xyz.block.artifactswap.core.publisher

import java.net.HttpURLConnection
import xyz.block.artifactswap.core.config.ArtifactSwapConfig
import xyz.block.artifactswap.core.maven.Metadata
import xyz.block.artifactswap.core.maven.Project
import xyz.block.artifactswap.core.network.ArtifactoryEndpoints

/** Implementation of BomRepository that uses Artifactory as the backend. */
class ArtifactoryBomRepository(
  private val artifactoryEndpoints: ArtifactoryEndpoints,
  private val config: ArtifactSwapConfig,
) : BomRepository {

  override suspend fun storeBom(project: Project, version: String): Result<Unit> {
    val response =
      artifactoryEndpoints.pushPom(
        repo = config.primaryRepositoryName,
        groupPath = config.primaryArtifactsMavenGroupArtifactoryPath,
        artifact = project.artifactId,
        version = version,
        filename = "${project.artifactId}-$version.pom",
        project = project,
      )

    return if (response.isSuccessful) {
      Result.success(Unit)
    } else {
      Result.failure(
        Exception(
          "Failed to push BOM (${response.code()}): " +
            (response.errorBody()?.string() ?: response.message())
        )
      )
    }
  }

  override suspend fun getBomMetadata(artifact: String): Result<Metadata?> {
    val response =
      artifactoryEndpoints.getMavenMetadata(
        repo = config.primaryRepositoryName,
        groupPath = config.primaryArtifactsMavenGroupArtifactoryPath,
        artifact = artifact,
      )

    return when {
      response.isSuccessful -> Result.success(response.body())
      response.code() == HttpURLConnection.HTTP_NOT_FOUND -> Result.success(null)
      else ->
        Result.failure(
          Exception(
            "Failed to fetch metadata (${response.code()}): " +
              (response.errorBody()?.string() ?: response.message())
          )
        )
    }
  }

  override suspend fun storeBomMetadata(metadata: Metadata): Result<Unit> {
    val response =
      artifactoryEndpoints.pushMetadata(
        repo = config.primaryRepositoryName,
        groupPath = config.primaryArtifactsMavenGroupArtifactoryPath,
        artifact = metadata.artifactId,
        metadata = metadata,
      )

    return if (response.isSuccessful) {
      Result.success(Unit)
    } else {
      Result.failure(
        Exception(
          "Failed to push metadata (${response.code()}): " +
            (response.errorBody()?.string() ?: response.message())
        )
      )
    }
  }
}
