package xyz.block.artifactswap.core.config

import java.io.Serializable

/**
 * Central configuration for all artifact swap operations.
 *
 * This class contains all configurable values that were previously hardcoded with
 * Square/Block-specific values. By centralizing these values, the artifact swap system can
 * eventually be adapted to work with different organizations, repositories, and artifact management
 * systems.
 */
data class ArtifactSwapConfig(
  // ============================================================================
  // Repository Configuration
  // ============================================================================

  /**
   * Name of the primary Artifactory/Maven repository containing built artifacts. This is used as
   * the repository name in Artifactory URLs.
   *
   * Example: "my-company-artifacts"
   */
  val primaryRepositoryName: String,

  // ============================================================================
  // Maven Group ID Configuration
  // ============================================================================

  /**
   * Maven group ID for the main artifacts. This is the base group ID used for all internally-built
   * artifacts.
   *
   * Example: "com.demo.artifactswap.artifacts"
   */
  val primaryArtifactsMavenGroup: String,

  // ============================================================================
  // API Endpoints
  // ============================================================================

  /**
   * Base URL for the production analytics/eventstream API. Used for logging events and telemetry
   * data.
   *
   * Example: "https://analytics.mycompany.com"
   */
  val eventstreamBaseUrl: String,

  // ============================================================================
  // Authentication & Credentials
  // ============================================================================

  /**
   * File name of the authentication token for publishing to Artifactory. This is typically used in
   * CI when updating artifacts in an internal repository.
   *
   * Example: "secrets.txt"
   */
  val artifactoryPublisherTokenFileName: String,

  // ============================================================================
  // Gradle Properties
  // ============================================================================

  // ============================================================================
  // Gradle Projects Settings
  // ============================================================================

  /** List of Gradle project paths to exclude from artifact swapping. */
  val excludeGradleProjects: List<String>,

  // ============================================================================
  // BOM Configuration
  // ============================================================================

  /**
   * Git branch name where BOM versions are available. The BOM loader searches for commits shared
   * between the current branch and this branch to find the most recent available BOM.
   *
   * Example: "origin/main" or "origin/artifact-sync-green-main"
   */
  val bomSourceBranchName: String,

  // ============================================================================
  // Artifactory
  // ============================================================================
  val artifactoryBaseUrl: String,

  // ============================================================================
  // Local Maven Repository
  // ============================================================================

  /**
   * Path to the local Maven repository directory. Defaults to ~/.m2/repository but can be
   * overridden for testing or custom setups.
   *
   * Example: "/tmp/custom-maven-repo" or "/Users/user/.m2/repository"
   */
  val mavenLocalDirectory: String = DEFAULT_MAVEN_LOCAL_DIRECTORY,
) : Serializable {
  val primaryArtifactsMavenGroupArtifactoryPath = primaryArtifactsMavenGroup.replace('.', '/')

  companion object Defaults {
    const val EVENTSTREAM_BASE_URL = "https://analytics.example.com"
    const val ARTIFACTORY_BASE_URL = "https://artifactory.example.com"
    const val DEFAULT_MAVEN_LOCAL_DIRECTORY = "\${user.home}/.m2/repository"
  }
}
