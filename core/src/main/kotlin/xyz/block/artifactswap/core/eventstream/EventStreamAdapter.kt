package xyz.block.artifactswap.core.eventstream

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.kotlin.logger

/**
 * Generic adapter for sending events to Eventstream.
 *
 * This class provides a reusable implementation for transforming domain-specific events into
 * EventstreamEvents and sending them to the eventstream service.
 *
 * @param T The domain event type (e.g., ArtifactDownloaderEvent, TaskRunnerServiceResult)
 * @param eventstream The underlying Eventstream service
 * @param ioDispatcher Dispatcher for IO operations
 * @param catalogName The catalog name for the events
 */
open class EventStreamAdapter(
  private val eventstream: Eventstream,
  private val ioDispatcher: CoroutineDispatcher,
  private val catalogName: String,
) {
  /**
   * Sends a list of domain events to the eventstream.
   *
   * @param events List of domain events to send
   * @return True if events were sent successfully, false otherwise
   */
  open suspend fun sendEvents(events: List<Any>): Boolean {
    return withContext(ioDispatcher) {
      try {
        val eventstreamEvents =
          events.map { event ->
            EventstreamEvent(catalogName = catalogName, appName = "artifact_sync", event = event)
          }
        logger.debug {
          "Sending ${eventstreamEvents.size} events to eventstream (catalog: $catalogName)"
        }
        val success = eventstream.sendEvents(eventstreamEvents)
        if (success) {
          logger.debug { "Successfully sent events to eventstream" }
        } else {
          logger.error { "Failed to send events to eventstream: $events" }
        }
        success
      } catch (e: IllegalStateException) {
        // This can happen when schema change is deployed without updating the jar that is sending
        // events
        logger.error(e) { "Failed to log event to eventstream (possible schema mismatch)" }
        false
      } catch (e: Exception) {
        logger.error(e) { "Error sending events to eventstream" }
        false
      }
    }
  }
}
