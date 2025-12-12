package xyz.block.artifactswap.core.publisher

import xyz.block.artifactswap.core.maven.Dependency
import xyz.block.artifactswap.core.maven.Metadata
import xyz.block.artifactswap.core.maven.Project

/** Fake implementation of BomRepository for testing. */
class FakeBomRepository : BomRepository {
  var availableDependencies = mutableMapOf<String, String>()
  var publishBomResult: Result<Unit> = Result.success(Unit)
  var fetchMetadataResult: Result<Metadata?> = Result.success(null)
  var publishMetadataResult: Result<Unit> = Result.success(Unit)

  val publishedBoms = mutableListOf<Project>()
  val publishedMetadata = mutableListOf<Metadata>()

  override suspend fun fetchAvailableDependencies(
    projectHashes: Map<String, String>
  ): List<Dependency> {
    return projectHashes
      .filter { (artifact, version) -> availableDependencies[artifact] == version }
      .map { (artifact, version) ->
        Dependency(
          groupId = "xyz.block.artifactswap.artifacts",
          artifactId = artifact,
          version = version,
        )
      }
  }

  override suspend fun publishBom(project: Project, version: String): Result<Unit> {
    if (publishBomResult.isSuccess) {
      publishedBoms.add(project)
    }
    return publishBomResult
  }

  override suspend fun fetchBomMetadata(artifact: String): Result<Metadata?> {
    return fetchMetadataResult
  }

  override suspend fun publishBomMetadata(metadata: Metadata): Result<Unit> {
    if (publishMetadataResult.isSuccess) {
      publishedMetadata.add(metadata)
    }
    return publishMetadataResult
  }
}
