package xyz.block.artifactswap.core.module_selector

import xyz.block.artifactswap.core.maven.Project

/** Fake implementation of LocalArtifactRepository for testing. */
class FakeLocalArtifactRepository : LocalArtifactRepository {
  var installedArtifacts: Set<InstalledArtifact> = emptySet()
  var installedBom: Project? = null

  override suspend fun getInstalledArtifacts(bom: Project): Result<Set<InstalledArtifact>> {
    return Result.success(installedArtifacts)
  }

  override suspend fun getInstalledBom(bomVersion: String): Result<Project> {
    return installedBom?.let { Result.success(it) }
      ?: Result.failure(NoSuchElementException("BOM not found"))
  }
}
