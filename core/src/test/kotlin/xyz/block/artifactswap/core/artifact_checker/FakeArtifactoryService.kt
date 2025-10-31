package xyz.block.artifactswap.core.artifact_checker

import okhttp3.ResponseBody
import retrofit2.Response
import xyz.block.artifactswap.core.maven.Metadata
import xyz.block.artifactswap.core.maven.Project
import xyz.block.artifactswap.core.network.ArtifactoryEndpoints
import xyz.block.artifactswap.core.network.ArtifactoryService

class FakeArtifactoryEndpointsForArtifactChecker : ArtifactoryEndpoints {
    var existingArtifacts = setOf<Pair<String, String>>()

    override suspend fun getMavenMetadata(repo: String, artifact: String): Response<Metadata> {
        return Response.error(404, ResponseBody.create(null, "Not needed for these tests"))
    }

    override suspend fun getPom(repo: String, artifact: String, version: String): Response<Project> {
        // Return a basic POM with "jar" packaging for testing
        return if (existingArtifacts.contains(artifact to version)) {
            Response.success(
                Project(
                    groupId = "com.squareup.register.sandbags",
                    artifactId = artifact,
                    version = version,
                    name = artifact,
                    dependencyManagement = xyz.block.artifactswap.core.maven.DependencyManagement(
                        dependencies = xyz.block.artifactswap.core.maven.Dependencies(emptyList())
                    ),
                    packaging = "jar"
                )
            )
        } else {
            Response.error(404, ResponseBody.create(null, "POM not found"))
        }
    }

    override suspend fun headArtifact(
        repo: String,
        artifact: String,
        version: String,
        packaging: String
    ): Response<Void> {
        return if (existingArtifacts.contains(artifact to version)) {
            Response.success(null)
        } else {
            Response.error(404, ResponseBody.create(null, "Artifact not found"))
        }
    }

    override suspend fun pushMetadata(repo: String, artifact: String, metadata: Metadata): Response<Unit> {
        throw NotImplementedError("Not needed for these tests")
    }

    override suspend fun pushPom(
        repo: String,
        artifact: String,
        version: String,
        filename: String,
        project: Project
    ): Response<Unit> {
        throw NotImplementedError("Not needed for these tests")
    }

    override suspend fun getFile(
        repo: String,
        group: String,
        artifact: String,
        version: String,
        ext: String
    ): Response<ResponseBody> {
        return Response.error(404, ResponseBody.create(null, "Not needed for these tests"))
    }
}

fun createFakeArtifactoryService(existingArtifacts: Set<Pair<String, String>> = emptySet()): ArtifactoryService {
    val fakeEndpoints = FakeArtifactoryEndpointsForArtifactChecker().apply {
        this.existingArtifacts = existingArtifacts
    }
    return ArtifactoryService(fakeEndpoints)
}
