package xyz.block.artifactswap.cli.commands

import xyz.block.artifactswap.core.task_runner.models.TaskRunnerServiceResult
import xyz.block.artifactswap.core.task_runner.services.TaskRunnerEventStream

class FakeTaskRunnerEventStream : TaskRunnerEventStream {
    val receivedResults = mutableListOf<TaskRunnerServiceResult>()

    override suspend fun sendResults(results: List<TaskRunnerServiceResult>): Boolean {
        receivedResults.addAll(results)
        return true
    }
}
