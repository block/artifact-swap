package xyz.block.artifactswap.core.publisher

import xyz.block.artifactswap.core.publisher.models.BomPublisherResult
import xyz.block.artifactswap.core.publisher.services.BomPublisherEventStream

class FakeBomPublisherEventStream : BomPublisherEventStream {
    val receivedResults = mutableListOf<BomPublisherResult>()

    override suspend fun sendResults(results: List<BomPublisherResult>): Boolean {
        receivedResults.addAll(results)
        return true
    }
}
