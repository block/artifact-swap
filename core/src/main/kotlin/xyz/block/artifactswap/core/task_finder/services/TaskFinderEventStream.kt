package xyz.block.artifactswap.core.task_finder.services

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.kotlin.logger
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.task_finder.models.TaskFinderServiceResult
import xyz.block.artifactswap.core.task_finder.models.toEvent

/**
 * Interface for sending task finder events to analytics.
 */
interface TaskFinderEventStream {
    /**
     * Sends task finder results to the event stream.
     *
     * @param results List of task finder results to send
     * @return True if events were sent successfully, false otherwise
     */
    suspend fun sendResults(results: List<TaskFinderServiceResult>): Boolean
}

/**
 * Real implementation of TaskFinderEventStream using Eventstream.
 */
class RealTaskFinderEventStream(
    private val eventstream: Eventstream,
    private val ioDispatcher: CoroutineDispatcher
) : TaskFinderEventStream {

    override suspend fun sendResults(results: List<TaskFinderServiceResult>): Boolean {
        return withContext(ioDispatcher) {
            try {
                val events = results.map { result ->
                    xyz.block.artifactswap.core.eventstream.EventstreamEvent(
                        catalogName = "artifact_sync_task_finder",
                        appName = "artifact_sync",
                        event = result.toEvent()
                    )
                }
                logger.debug { "Sending ${events.size} task finder events to eventstream" }
                val success = eventstream.sendEvents(events)
                if (success) {
                    logger.debug { "Successfully sent task finder events" }
                } else {
                    logger.error { "Failed to send task finder events" }
                }
                success
            } catch (e: Exception) {
                logger.error(e) { "Error sending task finder events" }
                false
            }
        }
    }
}
