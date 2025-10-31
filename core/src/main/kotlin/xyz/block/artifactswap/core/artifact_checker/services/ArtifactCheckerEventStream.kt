package xyz.block.artifactswap.core.artifact_checker.services

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.kotlin.logger
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.artifact_checker.models.ArtifactCheckerServiceResult
import xyz.block.artifactswap.core.artifact_checker.models.toEvent

/**
 * Interface for sending artifact checker events to analytics.
 */
interface ArtifactCheckerEventStream {
    /**
     * Sends artifact checker results to the event stream.
     *
     * @param results List of artifact checker results to send
     * @return True if events were sent successfully, false otherwise
     */
    suspend fun sendResults(results: List<ArtifactCheckerServiceResult>): Boolean
}

/**
 * Real implementation of ArtifactCheckerEventStream using Eventstream.
 */
class RealArtifactCheckerEventStream(
    private val eventstream: Eventstream,
    private val ioDispatcher: CoroutineDispatcher
) : ArtifactCheckerEventStream {

    override suspend fun sendResults(results: List<ArtifactCheckerServiceResult>): Boolean {
        return withContext(ioDispatcher) {
            try {
                val events = results.map { result ->
                    xyz.block.artifactswap.core.eventstream.EventstreamEvent(
                        catalogName = "artifact_sync_artifact_checker",
                        appName = "artifact_sync",
                        event = result.toEvent()
                    )
                }
                logger.debug { "Sending ${events.size} artifact checker events to eventstream" }
                val success = eventstream.sendEvents(events)
                if (success) {
                    logger.debug { "Successfully sent artifact checker events" }
                } else {
                    logger.error { "Failed to send artifact checker events" }
                }
                success
            } catch (e: IllegalStateException) {
                // this can happen when schema change is deployed without updating the jar that is sending
                // events
                logger.error(e) { "Failed to log event to eventstream" }
                false
            } catch (e: Exception) {
                logger.error(e) { "Error sending artifact checker events" }
                false
            }
        }
    }
}
