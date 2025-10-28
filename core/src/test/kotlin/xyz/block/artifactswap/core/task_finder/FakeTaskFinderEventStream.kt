package xyz.block.artifactswap.core.task_finder

import xyz.block.artifactswap.core.task_finder.models.TaskFinderServiceResult
import xyz.block.artifactswap.core.task_finder.services.TaskFinderEventStream

class FakeTaskFinderEventStream : TaskFinderEventStream {
    val receivedResults = mutableListOf<TaskFinderServiceResult>()

    override suspend fun sendResults(results: List<TaskFinderServiceResult>): Boolean {
        receivedResults.addAll(results)
        return true
    }
}
