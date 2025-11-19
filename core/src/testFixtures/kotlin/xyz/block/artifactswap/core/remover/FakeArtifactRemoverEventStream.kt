package xyz.block.artifactswap.core.remover

import xyz.block.artifactswap.core.remover.models.ArtifactRemoverResult
import xyz.block.artifactswap.core.remover.services.ArtifactRemoverEventStream

/** Fake implementation of ArtifactRemoverEventStream for testing. */
class FakeArtifactRemoverEventStream : ArtifactRemoverEventStream {
  val receivedResults = mutableListOf<ArtifactRemoverResult>()

  override suspend fun sendResults(results: List<ArtifactRemoverResult>): Boolean {
    receivedResults.addAll(results)
    return true
  }
}
