package xyz.block.artifactswap.core.publisher

import xyz.block.artifactswap.core.maven.Dependency

/**
 * Repository for checking the availability of published artifacts. This is separate from
 * [BomRepository] because checking artifact availability is not a BOM-specific concern.
 */
interface PublishedArtifactRepository {

  /**
   * Fetches available dependencies from the repository based on project hashes.
   *
   * @param projectHashes Map of artifact IDs to their versions
   * @return List of dependencies that are available in the repository
   */
  suspend fun getAvailableDependencies(projectHashes: Map<String, String>): List<Dependency>
}
