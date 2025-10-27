package xyz.block.artifactswap.core.publisher

import xyz.block.artifactswap.core.publisher.services.ProjectHashReader
import java.nio.file.Path

class FakeProjectHashReader : ProjectHashReader {
    var projectHashes: Result<Map<String, String>> = Result.success(emptyMap())

    override suspend fun readProjectHashes(hashPath: Path): Result<Map<String, String>> {
        return projectHashes
    }
}
