package xyz.block.artifactswap.core.publisher

import xyz.block.artifactswap.core.maven.Dependency

/** Fake implementation of PublishedArtifactRepository for testing. */
class FakePublishedArtifactRepository : PublishedArtifactRepository {
  var availableDependencies = mutableMapOf<String, String>()

  override suspend fun getAvailableDependencies(
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
}
