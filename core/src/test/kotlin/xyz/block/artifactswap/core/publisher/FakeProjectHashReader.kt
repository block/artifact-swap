package xyz.block.artifactswap.core.publisher

import java.nio.file.Path
import xyz.block.artifactswap.core.publisher.services.ProjectHashReader

class FakeProjectHashReader : ProjectHashReader {
  var projectHashes: Result<Map<String, String>> = Result.success(emptyMap())

  override suspend fun readProjectHashes(hashPath: Path): Result<Map<String, String>> {
    return projectHashes
  }
}
