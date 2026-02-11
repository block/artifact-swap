package xyz.block.artifactswap.core.publisher.services

import java.io.IOException
import java.nio.file.Path
import xyz.block.artifactswap.core.hashing.parseProjectHashFile

/** Service for reading project hash mappings from file. */
interface ProjectHashReader {
  /**
   * Reads project hashes from the specified file. Returns a map of artifact name to version hash.
   */
  suspend fun readProjectHashes(hashPath: Path): Result<Map<String, String>>
}

/** Real implementation that reads from filesystem. */
class RealProjectHashReader : ProjectHashReader {
  override suspend fun readProjectHashes(hashPath: Path): Result<Map<String, String>> {
    return try {
      // Parse raw project paths then convert to artifact names
      val projectHashMap =
        hashPath.parseProjectHashFile().mapKeys { (key, _) -> key.projectToArtifact() }
      Result.success(projectHashMap)
    } catch (e: IOException) {
      Result.failure(e)
    }
  }

  private fun String.projectToArtifact(): String {
    return drop(1).replace(':', '_')
  }
}
