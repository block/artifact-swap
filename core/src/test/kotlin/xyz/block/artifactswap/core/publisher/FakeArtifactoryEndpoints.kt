package xyz.block.artifactswap.core.publisher

import okhttp3.ResponseBody
import retrofit2.Response
import xyz.block.artifactswap.core.maven.Metadata
import xyz.block.artifactswap.core.maven.Project
import xyz.block.artifactswap.core.network.ArtifactoryEndpoints

class FakeArtifactoryEndpoints : ArtifactoryEndpoints {
    var metadataResponses = mutableMapOf<String, Response<Metadata>>()
    var pomResponses = mutableMapOf<String, Response<Project>>()
    var pushPomResponses = mutableListOf<Response<Unit>>()
    var pushMetadataResponses = mutableListOf<Response<Unit>>()

    val pushedPoms = mutableListOf<Project>()
    val pushedMetadata = mutableListOf<Metadata>()

    override suspend fun getMavenMetadata(repo: String, artifact: String): Response<Metadata> {
        return metadataResponses[artifact] ?: Response.error(404, ResponseBody.create(null, "Not found"))
    }

    override suspend fun getPom(repo: String, artifact: String, version: String): Response<Project> {
        return pomResponses["$artifact:$version"] ?: Response.error(404, ResponseBody.create(null, "Not found"))
    }

    override suspend fun headArtifact(
        repo: String,
        artifact: String,
        version: String,
        packaging: String
    ): Response<Void> {
        return Response.success(null)
    }

    override suspend fun pushMetadata(repo: String, artifact: String, metadata: Metadata): Response<Unit> {
        pushedMetadata.add(metadata)
        return pushMetadataResponses.removeFirstOrNull() ?: Response.success(Unit)
    }

    override suspend fun pushPom(
        repo: String,
        artifact: String,
        version: String,
        filename: String,
        project: Project
    ): Response<Unit> {
        pushedPoms.add(project)
        return pushPomResponses.removeFirstOrNull() ?: Response.success(Unit)
    }

    override suspend fun getFile(
        repo: String,
        group: String,
        artifact: String,
        version: String,
        ext: String
    ): Response<ResponseBody> {
        return Response.error(404, ResponseBody.create(null, "Not found"))
    }
}
