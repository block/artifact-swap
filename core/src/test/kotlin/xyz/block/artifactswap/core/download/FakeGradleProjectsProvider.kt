package xyz.block.artifactswap.core.download

import xyz.block.artifactswap.core.gradle.GradleProjectsProvider
import xyz.block.artifactswap.core.gradle.ProjectHashingInfo

class FakeGradleProjectsProvider(private val projects: List<ProjectHashingInfo>) : GradleProjectsProvider {
    override suspend fun getProjectHashingInfos(): Result<List<ProjectHashingInfo>> {
        return Result.success(projects)
    }

    override suspend fun cleanup() {}
}
