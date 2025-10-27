package xyz.block.artifactswap.core.publisher.services

import kotlinx.coroutines.withContext
import org.apache.logging.log4j.kotlin.logger
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.publisher.models.BomPublisherResult
import xyz.block.artifactswap.core.publisher.models.toEvent
import kotlin.coroutines.CoroutineContext

/**
 * Service for sending BOM publisher events to eventstream.
 */
interface BomPublisherEventStream {
    suspend fun sendResults(results: List<BomPublisherResult>): Boolean
}

/**
 * Real implementation that sends to eventstream.
 */
class RealBomPublisherEventStream(
    private val eventstream: Eventstream,
    private val ioDispatcher: CoroutineContext
) : BomPublisherEventStream {
    override suspend fun sendResults(results: List<BomPublisherResult>): Boolean {
        return try {
            withContext(ioDispatcher) {
                val events = results.map { it.toEvent().toEventStreamEvent() }
                logger.debug { "Sending ${events.size} events to es2" }
                val isSuccessful = eventstream.sendEvents(events)
                if (isSuccessful) {
                    logger.debug { "Successfully sent events to es2" }
                } else {
                    logger.error { "Failed to send events to es2: $results" }
                }
                isSuccessful
            }
        } catch (e: Exception) {
            logger.error { "Unable to log events, schema may have changed since last deploy: $e" }
            false
        }
    }
}
