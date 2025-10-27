package xyz.block.artifactswap.core.hashing

import xyz.block.artifactswap.core.gradle.GradleProjectsProvider
import xyz.block.artifactswap.core.gradle.ProjectHashingInfo

class FakeGradleProjectsProvider : GradleProjectsProvider {
    var projectHashingInfos: Result<List<ProjectHashingInfo>> = Result.success(emptyList())
    var cleanupCalled = false

    override suspend fun getProjectHashingInfos(): Result<List<ProjectHashingInfo>> {
        return projectHashingInfos
    }

    override suspend fun cleanup() {
        cleanupCalled = true
    }
}
