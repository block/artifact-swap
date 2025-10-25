package xyz.block.artifactswap.core.remover

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import xyz.block.artifactswap.core.remover.services.InstalledBom
import xyz.block.artifactswap.core.remover.services.InstalledProject
import xyz.block.artifactswap.core.remover.services.LocalArtifactRepository
import xyz.block.artifactswap.core.remover.services.RepositoryStats
import kotlin.time.Duration.Companion.milliseconds

class FakeLocalArtifactRepository : LocalArtifactRepository {
    var installedProjects: List<InstalledProject> = emptyList()
    var installedBoms: List<InstalledBom> = emptyList()
    var repositoryStats: RepositoryStats? = null

    val deletedProjects = mutableListOf<InstalledProject>()
    val deletedBoms = mutableListOf<InstalledBom>()

    // Configure which deletions should fail
    val projectDeletionFailures = mutableSetOf<InstalledProject>()
    val bomDeletionFailures = mutableSetOf<InstalledBom>()

    override fun getAllInstalledProjects(): Flow<InstalledProject> {
        return installedProjects.asFlow()
    }

    override suspend fun getInstalledBomsByRecency(count: Int): List<InstalledBom> {
        return installedBoms.take(count)
    }

    override suspend fun deleteInstalledProjectVersions(installedProject: InstalledProject): List<String> {
        deletedProjects.add(installedProject)

        return if (projectDeletionFailures.contains(installedProject)) {
            // Simulate partial failure - only delete half the versions
            installedProject.versions.take(installedProject.versions.size / 2).toList()
        } else {
            installedProject.versions.toList()
        }
    }

    override suspend fun deleteInstalledBom(installedBom: InstalledBom): Boolean {
        deletedBoms.add(installedBom)
        return !bomDeletionFailures.contains(installedBom)
    }

    override suspend fun measureRepository(): RepositoryStats? {
        return repositoryStats ?: RepositoryStats(
            countInstalledProjects = installedProjects.size.toLong(),
            countInstalledArtifacts = installedProjects.sumOf { it.versions.size }.toLong(),
            countInstalledBoms = installedBoms.size.toLong(),
            sizeOfInstalledArtifactsBytes = 1024L * 1024L * installedProjects.size,
            sizeOfInstalledBomsBytes = 1024L * 100L * installedBoms.size,
            overallRepoSizeBytes = 1024L * 1024L * (installedProjects.size + installedBoms.size),
            installedArtifactsMeasurementDuration = 100.milliseconds,
            installedBomsMeasurementDuration = 50.milliseconds,
            measurementDuration = 200.milliseconds
        )
    }
}
