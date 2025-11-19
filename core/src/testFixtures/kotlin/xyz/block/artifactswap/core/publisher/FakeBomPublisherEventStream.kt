package xyz.block.artifactswap.core.publisher

import xyz.block.artifactswap.core.publisher.models.BomPublisherResult
import xyz.block.artifactswap.core.publisher.services.BomPublisherEventStream

/** Fake implementation of BomPublisherEventStream for testing. */
class FakeBomPublisherEventStream : BomPublisherEventStream {
  val receivedResults = mutableListOf<BomPublisherResult>()

  override suspend fun sendResults(results: List<BomPublisherResult>): Boolean {
    receivedResults.addAll(results)
    return true
  }
}
