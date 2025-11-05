package xyz.block.artifactswap.core.remover.services

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.remover.models.ArtifactRemoverResult

interface ArtifactRemoverEventStream {
  suspend fun sendResults(results: List<ArtifactRemoverResult>): Boolean
}

class RealArtifactRemoverEventStream(
  private val eventstream: Eventstream,
  private val ioDispatcher: CoroutineContext,
) : ArtifactRemoverEventStream {

  override suspend fun sendResults(results: List<ArtifactRemoverResult>): Boolean {
    return withContext(ioDispatcher) {
      return@withContext eventstream.sendEvents(
        results.map { it.toArtifactRemoverEvent().toEventStreamEvent() }
      )
    }
  }
}
