package xyz.block.artifactswap.core.gradle

/**
 * Provides information about local Gradle projects that is relevant for hashing.
 */
interface GradleProjectsProvider {

    /**
     * Determines the participating Gradle projects that are relevant for hashing and returns a
     * list of data classes containing the relevant information from those gradle projects to compute
     * the hashes of the projects.
     */
    suspend fun getProjectHashingInfos(): Result<List<ProjectHashingInfo>>

    /**
     * Release any resources that were used to fetch the project information.
     */
    suspend fun cleanup()
}
