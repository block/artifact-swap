package xyz.block.artifactswap.core.task_runner.services

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.kotlin.logger
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.task_runner.models.TaskRunnerServiceResult
import xyz.block.artifactswap.core.task_runner.models.toEvent

/**
 * Interface for sending task runner events to analytics.
 */
interface TaskRunnerEventStream {
    /**
     * Sends task runner results to the event stream.
     *
     * @param results List of task runner results to send
     * @return True if events were sent successfully, false otherwise
     */
    suspend fun sendResults(results: List<TaskRunnerServiceResult>): Boolean
}

/**
 * Real implementation of TaskRunnerEventStream using Eventstream.
 */
class RealTaskRunnerEventStream(
    private val eventstream: Eventstream,
    private val ioDispatcher: CoroutineDispatcher
) : TaskRunnerEventStream {

    override suspend fun sendResults(results: List<TaskRunnerServiceResult>): Boolean {
        return withContext(ioDispatcher) {
            try {
                val events = results.map { result ->
                    xyz.block.artifactswap.core.eventstream.EventstreamEvent(
                        catalogName = "artifact_sync_task_runner",
                        appName = "artifact_sync",
                        event = result.toEvent()
                    )
                }
                logger.debug { "Sending ${events.size} task runner events to eventstream" }
                val success = eventstream.sendEvents(events)
                if (success) {
                    logger.debug { "Successfully sent task runner events" }
                } else {
                    logger.error { "Failed to send task runner events" }
                }
                success
            } catch (e: Exception) {
                logger.error(e) { "Error sending task runner events" }
                false
            }
        }
    }
}
