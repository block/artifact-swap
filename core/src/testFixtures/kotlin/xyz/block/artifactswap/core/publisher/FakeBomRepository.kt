package xyz.block.artifactswap.core.publisher

import xyz.block.artifactswap.core.maven.Metadata
import xyz.block.artifactswap.core.maven.Project

/** Fake implementation of BomRepository for testing. */
class FakeBomRepository : BomRepository {
  var storeBomResult: Result<Unit> = Result.success(Unit)
  var getMetadataResult: Result<Metadata?> = Result.success(null)
  var storeMetadataResult: Result<Unit> = Result.success(Unit)

  val storedBoms = mutableListOf<Project>()
  val storedMetadata = mutableListOf<Metadata>()

  override suspend fun storeBom(project: Project, version: String): Result<Unit> {
    if (storeBomResult.isSuccess) {
      storedBoms.add(project)
    }
    return storeBomResult
  }

  override suspend fun getBomMetadata(artifact: String): Result<Metadata?> {
    return getMetadataResult
  }

  override suspend fun storeBomMetadata(metadata: Metadata): Result<Unit> {
    if (storeMetadataResult.isSuccess) {
      storedMetadata.add(metadata)
    }
    return storeMetadataResult
  }
}
