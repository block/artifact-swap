package xyz.block.artifactswap.cli.commands

import okhttp3.ResponseBody
import retrofit2.Response
import xyz.block.artifactswap.core.maven.Metadata
import xyz.block.artifactswap.core.maven.Project
import xyz.block.artifactswap.core.network.ArtifactoryEndpoints
import xyz.block.artifactswap.core.publisher.models.BomPublisherResult
import xyz.block.artifactswap.core.publisher.services.BomPublisherEventStream
import xyz.block.artifactswap.core.publisher.services.ProjectHashReader
import java.nio.file.Path

// Test fakes for CLI tests
internal class FakeBomPublisherEventStream : BomPublisherEventStream {
    val receivedResults = mutableListOf<BomPublisherResult>()

    override suspend fun sendResults(results: List<BomPublisherResult>): Boolean {
        receivedResults.addAll(results)
        return true
    }
}

internal class FakeProjectHashReader : ProjectHashReader {
    var projectHashes: Result<Map<String, String>> = Result.success(emptyMap())

    override suspend fun readProjectHashes(hashPath: Path): Result<Map<String, String>> {
        return projectHashes
    }
}

internal class FakeArtifactoryEndpoints : ArtifactoryEndpoints {
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
