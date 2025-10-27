package xyz.block.artifactswap.core.publisher.services

import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.useLines

/**
 * Service for reading project hash mappings from file.
 */
interface ProjectHashReader {
    /**
     * Reads project hashes from the specified file.
     * Returns a map of artifact name to version hash.
     */
    suspend fun readProjectHashes(hashPath: Path): Result<Map<String, String>>
}

/**
 * Real implementation that reads from filesystem.
 */
class RealProjectHashReader : ProjectHashReader {
    override suspend fun readProjectHashes(hashPath: Path): Result<Map<String, String>> {
        return try {
            val projectHashMap = hashPath.useLines { lines ->
                lines.map { it.split('|') }
                    .filter { it.size == 2 }
                    .associate { it.first().projectToArtifact() to it.last() }
            }
            Result.success(projectHashMap)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    private fun String.projectToArtifact(): String {
        return drop(1).replace(':', '_')
    }
}
