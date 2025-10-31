package xyz.block.artifactswap.core.artifact_checker

import xyz.block.artifactswap.core.artifact_checker.models.ArtifactCheckerServiceResult
import xyz.block.artifactswap.core.artifact_checker.services.ArtifactCheckerEventStream

class FakeArtifactCheckerEventStream : ArtifactCheckerEventStream {
    val receivedResults = mutableListOf<ArtifactCheckerServiceResult>()

    override suspend fun sendResults(results: List<ArtifactCheckerServiceResult>): Boolean {
        receivedResults.addAll(results)
        return true
    }
}
