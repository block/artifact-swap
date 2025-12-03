package xyz.block.artifactswap.core.module_selector

import xyz.block.artifactswap.core.maven.Dependencies
import xyz.block.artifactswap.core.maven.DependencyManagement
import xyz.block.artifactswap.core.maven.Project

/** Fake implementation of ArtifactSwapBomHelper for testing. */
class FakeArtifactSwapBomHelper : ArtifactSwapBomHelper {
  var bomVersion: String = "test-bom-version"
  var bom: Project? = null

  override suspend fun findBestBomVersion(): Result<String> {
    return Result.success(bomVersion)
  }

  override suspend fun loadBom(bomVersion: String): Result<Project> {
    return bom?.let { Result.success(it) }
      ?: Result.success(
        Project(
          groupId = "test",
          artifactId = "bom",
          version = bomVersion,
          name = "Test BOM",
          dependencyManagement =
            DependencyManagement(dependencies = Dependencies(dependency = emptyList())),
        )
      )
  }
}
