package xyz.block.artifactswap.core.publisher

import xyz.block.artifactswap.core.maven.Metadata
import xyz.block.artifactswap.core.maven.Project

/** Repository for storing and retrieving BOM (Bill of Materials) artifacts. */
interface BomRepository {

  /**
   * Stores a BOM project in the repository.
   *
   * @param project The BOM project to store
   * @param version The version of the BOM
   * @return Result indicating success or failure
   */
  suspend fun storeBom(project: Project, version: String): Result<Unit>

  /**
   * Gets existing BOM metadata from the repository.
   *
   * @param artifact The artifact ID (typically "bom")
   * @return Result containing the metadata or null if not found
   */
  suspend fun getBomMetadata(artifact: String): Result<Metadata?>

  /**
   * Stores or updates BOM metadata in the repository.
   *
   * @param metadata The metadata to store
   * @return Result indicating success or failure
   */
  suspend fun storeBomMetadata(metadata: Metadata): Result<Unit>
}
