package xyz.block.artifactswap.core.publisher

import java.net.HttpURLConnection
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.apache.logging.log4j.kotlin.logger
import xyz.block.artifactswap.core.config.ArtifactSwapConfig
import xyz.block.artifactswap.core.maven.Dependency
import xyz.block.artifactswap.core.network.ArtifactoryEndpoints

/** Implementation of [PublishedArtifactRepository] that uses Artifactory as the backend. */
class ArtifactoryPublishedArtifactRepository(
  private val artifactoryEndpoints: ArtifactoryEndpoints,
  private val config: ArtifactSwapConfig,
) : PublishedArtifactRepository {
  override suspend fun getAvailableDependencies(
    projectHashes: Map<String, String>
  ): List<Dependency> = coroutineScope {
    return@coroutineScope projectHashes
      .map { (artifact, version) ->
        // Query the artifactory repository to see if the artifact exists
        async {
          val response =
            artifactoryEndpoints.getMavenMetadata(
              repo = config.primaryRepositoryName,
              groupPath = config.primaryArtifactsMavenGroupArtifactoryPath,
              artifact = artifact,
            )
          if (response.isSuccessful) {
            val metadata = response.body()
            if (metadata == null) {
              logger.info { "Got OK, but metadata was null for $artifact" }
              return@async null
            }

            // Artifact may exist, but it's possible the version hashed is not present or failed to
            // publish
            if (metadata.versioning.versions.version.asReversed().contains(version)) {
              return@async Dependency(
                groupId = metadata.groupId,
                artifactId = metadata.artifactId,
                version = version,
              )
            }
            logger.warn { "Artifact $artifact version not found in metadata" }
            return@async null
          } else if (response.code() != HttpURLConnection.HTTP_NOT_FOUND) {
            logger.error(
              "Unable to get maven metadata for $artifact (${response.code()}): " +
                (response.errorBody()?.string() ?: response.message())
            )
            return@async null
          }
          return@async null
        }
      }
      .awaitAll()
      .filterNotNull()
  }
}
