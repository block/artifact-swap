package xyz.block.artifactswap.core.download

import xyz.block.artifactswap.core.download.models.ArtifactDownloaderEvent
import xyz.block.artifactswap.core.download.services.ArtifactDownloaderEventStream

class FakeEventStream : ArtifactDownloaderEventStream {
    val receivedEvents = mutableListOf<ArtifactDownloaderEvent>()

    override suspend fun sendEvents(events: List<ArtifactDownloaderEvent>): Boolean {
        receivedEvents.addAll(events)
        return true
    }
}
