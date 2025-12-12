package xyz.block.artifactswap.core.publisher

import xyz.block.artifactswap.core.maven.Dependency
import xyz.block.artifactswap.core.maven.Metadata
import xyz.block.artifactswap.core.maven.Project

/** Repository for publishing and fetching BOM (Bill of Materials) artifacts. */
interface BomRepository {

  /**
   * Fetches available dependencies from the repository based on project hashes.
   *
   * @param projectHashes Map of artifact IDs to their versions
   * @return List of dependencies that are available in the repository
   */
  suspend fun fetchAvailableDependencies(projectHashes: Map<String, String>): List<Dependency>

  /**
   * Publishes a BOM project to the repository.
   *
   * @param project The BOM project to publish
   * @param version The version of the BOM
   * @return Result indicating success or failure
   */
  suspend fun publishBom(project: Project, version: String): Result<Unit>

  /**
   * Fetches existing BOM metadata from the repository.
   *
   * @param artifact The artifact ID (typically "bom")
   * @return Result containing the metadata or null if not found
   */
  suspend fun fetchBomMetadata(artifact: String): Result<Metadata?>

  /**
   * Publishes or updates BOM metadata in the repository.
   *
   * @param metadata The metadata to publish
   * @return Result indicating success or failure
   */
  suspend fun publishBomMetadata(metadata: Metadata): Result<Unit>
}
