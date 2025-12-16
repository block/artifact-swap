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

  /**
   * Name of the secondary Artifactory/Maven repository if not all are present in primary.
   *
   * Example: "my-company-public-protos"
   */
  val secondaryRepositoryName: String,

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

  /**
   * Maven group ID for artifacts in secondary repository (e.g. if your org publishes artifacts
   * publicly and internally).
   *
   * Example: "com.mycompany.publicprotos"
   */
  val secondaryArtifactsMavenGroup: String,

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

  /**
   * Gradle property key for the generated protos version. Used to specify which version of
   * generated protocol buffer code to use.
   *
   * Example: "square.protosGeneratedVersion" or "mycompany.protosGeneratedVersion"
   */
  val protosGeneratedVersionProperty: String,

  /**
   * Gradle property key for the protos schema version. Used to specify which version of the
   * protocol buffer schema definitions to use.
   *
   * Example: "square.protosSchemaVersion" or "mycompany.protosSchemaVersion"
   */
  val protosSchemaVersionProperty: String,

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
) : Serializable {
  val primaryArtifactsMavenGroupArtifactoryPath = primaryArtifactsMavenGroup.replace('.', '/')

  companion object Defaults {
    const val SECONDARY_REPOSITORY_NAME = "demo-secondary-repo"
    const val SECONDARY_ARTIFACTS_MAVEN_GROUP = "com.demo.artifactswap.secondary"
    const val EVENTSTREAM_BASE_URL = "https://analytics.example.com"
    const val PROTOS_GENERATED_VERSION_PROPERTY = "square.protosGeneratedVersion"
    const val PROTOS_SCHEMA_VERSION_PROPERTY = "square.protosSchemaVersion"
    const val ARTIFACTORY_BASE_URL = "https://artifactory.example.com"
  }
}
