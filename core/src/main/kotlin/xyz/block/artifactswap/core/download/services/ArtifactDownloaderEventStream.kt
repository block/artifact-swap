package xyz.block.artifactswap.core.download.services

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext
import xyz.block.artifactswap.core.download.models.ArtifactDownloaderEvent
import xyz.block.artifactswap.core.eventstream.Eventstream

interface ArtifactDownloaderEventStream {
  suspend fun sendEvents(events: List<ArtifactDownloaderEvent>): Boolean
}

class RealEventStream(
  private val eventstream: Eventstream,
  private val ioDispatcher: CoroutineContext,
) : ArtifactDownloaderEventStream {

  override suspend fun sendEvents(events: List<ArtifactDownloaderEvent>): Boolean {
    return withContext(ioDispatcher) {
      return@withContext eventstream.sendEvents(events.map { it.toEventStreamEvent() })
    }
  }
}
