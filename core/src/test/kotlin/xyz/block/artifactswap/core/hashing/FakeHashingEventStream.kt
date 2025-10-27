package xyz.block.artifactswap.core.hashing

import xyz.block.artifactswap.core.hashing.models.HashingServiceResult
import xyz.block.artifactswap.core.hashing.services.HashingEventStream

class FakeHashingEventStream : HashingEventStream {
    val receivedResults = mutableListOf<HashingServiceResult>()

    override suspend fun sendResults(results: List<HashingServiceResult>): Boolean {
        receivedResults.addAll(results)
        return true
    }
}
