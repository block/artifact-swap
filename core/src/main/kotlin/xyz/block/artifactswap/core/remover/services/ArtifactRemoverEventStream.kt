package xyz.block.artifactswap.core.remover.services

import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.remover.models.ArtifactRemoverResult
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

interface ArtifactRemoverEventStream {
  suspend fun sendResults(results: List<ArtifactRemoverResult>): Boolean
}

class RealArtifactRemoverEventStream(
  private val eventstream: Eventstream,
  private val ioDispatcher: CoroutineContext
) : ArtifactRemoverEventStream {

  override suspend fun sendResults(results: List<ArtifactRemoverResult>): Boolean {
    return withContext(ioDispatcher) {
      return@withContext eventstream.sendEvents(results.map { it.toArtifactRemoverEvent().toEventStreamEvent() })
    }
  }
}
