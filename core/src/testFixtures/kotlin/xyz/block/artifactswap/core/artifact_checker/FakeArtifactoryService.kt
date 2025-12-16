package xyz.block.artifactswap.core.artifact_checker

import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import xyz.block.artifactswap.core.config.testArtifactSwapConfig
import xyz.block.artifactswap.core.maven.Metadata
import xyz.block.artifactswap.core.maven.Project
import xyz.block.artifactswap.core.network.ArtifactoryEndpoints
import xyz.block.artifactswap.core.network.ArtifactoryService

/** Fake implementation of ArtifactoryEndpoints for testing artifact checker functionality. */
class FakeArtifactoryEndpointsForArtifactChecker : ArtifactoryEndpoints {
  var existingArtifacts = setOf<Pair<String, String>>()

  override suspend fun getMavenMetadata(
    repo: String,
    groupPath: String,
    artifact: String,
  ): Response<Metadata> {
    return Response.error(404, "Not needed for these tests".toResponseBody(null))
  }

  override suspend fun getPom(
    repo: String,
    groupPath: String,
    artifact: String,
    version: String,
  ): Response<Project> {
    // Return a basic POM with "jar" packaging for testing
    return if (existingArtifacts.contains(artifact to version)) {
      Response.success(
        Project(
          groupId = "com.squareup.register.sandbags",
          artifactId = artifact,
          version = version,
          name = artifact,
          dependencyManagement =
            xyz.block.artifactswap.core.maven.DependencyManagement(
              dependencies = xyz.block.artifactswap.core.maven.Dependencies(emptyList())
            ),
          packaging = "jar",
        )
      )
    } else {
      Response.error(404, "POM not found".toResponseBody(null))
    }
  }

  override suspend fun headArtifact(
    repo: String,
    groupPath: String,
    artifact: String,
    version: String,
    packaging: String,
  ): Response<Void> {
    return if (existingArtifacts.contains(artifact to version)) {
      Response.success(null)
    } else {
      Response.error(404, "Artifact not found".toResponseBody(null))
    }
  }

  override suspend fun pushMetadata(
    repo: String,
    groupPath: String,
    artifact: String,
    metadata: Metadata,
  ): Response<Unit> {
    throw NotImplementedError("Not needed for these tests")
  }

  override suspend fun pushPom(
    repo: String,
    groupPath: String,
    artifact: String,
    version: String,
    filename: String,
    project: Project,
  ): Response<Unit> {
    throw NotImplementedError("Not needed for these tests")
  }

  override suspend fun getFile(
    repo: String,
    group: String,
    artifact: String,
    version: String,
    ext: String,
  ): Response<ResponseBody> {
    return Response.error(404, "Not needed for these tests".toResponseBody(null))
  }
}

/** Creates a fake ArtifactoryService for testing with configurable existing artifacts. */
fun createFakeArtifactoryService(
  existingArtifacts: Set<Pair<String, String>> = emptySet()
): ArtifactoryService {
  val fakeEndpoints =
    FakeArtifactoryEndpointsForArtifactChecker().apply {
      this.existingArtifacts = existingArtifacts
    }
  return ArtifactoryService(fakeEndpoints, testArtifactSwapConfig())
}
