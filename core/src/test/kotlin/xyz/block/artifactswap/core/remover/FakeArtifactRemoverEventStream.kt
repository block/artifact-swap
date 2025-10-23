package xyz.block.artifactswap.core.remover

import xyz.block.artifactswap.core.remover.models.ArtifactRemoverResult
import xyz.block.artifactswap.core.remover.services.ArtifactRemoverEventStream

class FakeArtifactRemoverEventStream : ArtifactRemoverEventStream {
    val receivedResults = mutableListOf<ArtifactRemoverResult>()

    override suspend fun sendResults(results: List<ArtifactRemoverResult>): Boolean {
        receivedResults.addAll(results)
        return true
    }
}
