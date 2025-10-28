package xyz.block.artifactswap.core.hashing.services

import kotlinx.coroutines.withContext
import org.apache.logging.log4j.kotlin.logger
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.hashing.models.HashingServiceResult
import xyz.block.artifactswap.core.hashing.models.toEvent
import kotlin.coroutines.CoroutineContext

/**
 * Service for sending hashing events to eventstream.
 */
interface HashingEventStream {
    suspend fun sendResults(results: List<HashingServiceResult>): Boolean
}

/**
 * Real implementation that sends to eventstream.
 */
class RealHashingEventStream(
    private val eventstream: Eventstream,
    private val ioDispatcher: CoroutineContext
) : HashingEventStream {
    override suspend fun sendResults(results: List<HashingServiceResult>): Boolean {
        return withContext(ioDispatcher) {
            runCatching {
                val events = results.map { it.toEvent().toEventStreamEvent() }
                logger.debug { "Sending ${events.size} events to es2" }
                val isSuccessful = eventstream.sendEvents(events)
                if (isSuccessful) {
                    logger.debug { "Successfully sent events to es2" }
                } else {
                    logger.error { "Failed to send events to es2: $results" }
                }
                isSuccessful
            }.getOrElse { exception ->
                logger.error { "Unable to log events: $exception" }
                false
            }
        }
    }
}
