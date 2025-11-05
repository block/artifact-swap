package xyz.block.artifactswap.core.network

import java.io.FileNotFoundException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import xyz.block.artifactswap.core.config.ArtifactSwapConfigHolder
import xyz.block.artifactswap.core.maven.Project

class ArtifactoryService(private val artifactoryEndpoints: ArtifactoryEndpoints) {
  private companion object {
    private val REPO = ArtifactSwapConfigHolder.instance.primaryRepositoryName
    private val ARTIFACTORY_GROUP_PATH_SEGMENT =
      ArtifactSwapConfigHolder.instance.primaryArtifactsMavenGroupArtifactoryPath
  }

  /**
   * Checks for the existence of all files for an artifact in Artifactory
   *
   * Throws [FileNotFoundException] if any files are missing
   */
  suspend fun artifactFilesExist(artifactName: String, version: String) = coroutineScope {
    // Read the POM to determine how the artifact is packaged
    val pom = getPom(artifactName, version)

    val otherArtifactPackagings =
      listOf(
        ".module", // Gradle module metadata
        "-sources.jar", // Sources for artifact
        ".${pom.packaging}", // The binary artifact (aar or jar)
      )
    otherArtifactPackagings.forEach { packaging ->
      launch { checkArtifact(artifactName, version, packaging) }
    }
  }

  suspend fun getPom(artifactName: String, version: String): Project {
    val pomResponse =
      artifactoryEndpoints.getPom(
        repo = REPO,
        groupPath = ARTIFACTORY_GROUP_PATH_SEGMENT,
        artifact = artifactName,
        version = version,
      )

    return pomResponse.body()
      ?: throw FileNotFoundException(
        "$artifactName-$version.pom is missing in artifactory (${pomResponse.code()})"
      )
  }

  private suspend fun checkArtifact(artifactName: String, version: String, packaging: String) {
    val response =
      artifactoryEndpoints.headArtifact(
        repo = REPO,
        groupPath = ARTIFACTORY_GROUP_PATH_SEGMENT,
        artifact = artifactName,
        version = version,
        packaging = packaging,
      )
    if (!response.isSuccessful) {
      throw FileNotFoundException(
        "$artifactName-$version$packaging is missing in artifactory (${response.code()})"
      )
    }
  }
}
