package xyz.block.artifactswap.core.download

import xyz.block.artifactswap.core.gradle.GradleProjectsProvider
import xyz.block.artifactswap.core.gradle.ProjectHashingInfo

/**
 * Fake implementation of GradleProjectsProvider for testing. Supports both constructor-based
 * initialization and mutable properties.
 */
class FakeGradleProjectsProvider(projects: List<ProjectHashingInfo> = emptyList()) :
  GradleProjectsProvider {

  var projectHashingInfos: Result<List<ProjectHashingInfo>> = Result.success(projects)
  var cleanupCalled = false

  override suspend fun getProjectHashingInfos(): Result<List<ProjectHashingInfo>> {
    return projectHashingInfos
  }

  override suspend fun cleanup() {
    cleanupCalled = true
  }
}
