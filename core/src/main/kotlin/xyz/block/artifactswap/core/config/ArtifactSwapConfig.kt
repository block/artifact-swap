package xyz.block.artifactswap.core.config

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
  val primaryRepositoryName: String = "artifact-swap-demo",

  /**
   * Name of the secondary Artifactory/Maven repository if not all are present in primary.
   *
   * Example: "my-company-public-protos"
   */
  val secondaryRepositoryName: String = "demo-secondary-repo",

  // ============================================================================
  // Maven Group ID Configuration
  // ============================================================================

  /**
   * Maven group ID for the main artifacts. This is the base group ID used for all internally-built
   * artifacts.
   *
   * Example: "com.demo.artifactswap.artifacts"
   */
  val primaryArtifactsMavenGroup: String = "com.demo.artifactswap.artifacts",

  /**
   * Maven group ID for artifacts in secondary repository (e.g. if your org publishes artifacts
   * publicly and internally).
   *
   * Example: "com.mycompany.publicprotos"
   */
  val secondaryArtifactsMavenGroup: String = "com.demo.artifactswap.secondary",

  // ============================================================================
  // API Endpoints
  // ============================================================================

  /**
   * Base URL for the production analytics/eventstream API. Used for logging events and telemetry
   * data.
   *
   * Example: "https://analytics.mycompany.com"
   */
  val eventstreamBaseUrl: String = "https://analytics.example.com",

  // ============================================================================
  // Authentication & Credentials
  // ============================================================================

  /**
   * File name of the authentication token for publishing to Artifactory. This is typically used in
   * CI when updating artifacts in an internal repository.
   *
   * Example: "secrets.txt"
   */
  val artifactoryPublisherTokenFileName: String = "secret-file-name-that-lives-in-ci",

  // ============================================================================
  // Gradle Properties
  // ============================================================================

  /**
   * Gradle property key for the generated protos version. Used to specify which version of
   * generated protocol buffer code to use.
   *
   * Example: "square.protosGeneratedVersion" or "mycompany.protosGeneratedVersion"
   */
  val protosGeneratedVersionProperty: String = "square.protosGeneratedVersion",

  /**
   * Gradle property key for the protos schema version. Used to specify which version of the
   * protocol buffer schema definitions to use.
   *
   * Example: "square.protosSchemaVersion" or "mycompany.protosSchemaVersion"
   */
  val protosSchemaVersionProperty: String = "square.protosSchemaVersion",

  // ============================================================================
  // Gradle Projects Settings
  // ============================================================================

  /** List of Gradle project paths to exclude from artifact swapping. */
  val excludeGradleProjects: List<String> = emptyList(),

  // ============================================================================
  // BOM Configuration
  // ============================================================================

  /**
   * Git branch name where BOM versions are available. The BOM loader searches for commits shared
   * between the current branch and this branch to find the most recent available BOM.
   *
   * Example: "origin/main" or "origin/artifact-sync-green-main"
   */
  val bomSourceBranchName: String = "origin/artifact-sync-green-main",

  // ============================================================================
  // Artifactory
  // ============================================================================
  val artifactoryBaseUrl: String = "https://artifactory.example.com",
) {
  val primaryArtifactsMavenGroupArtifactoryPath = primaryArtifactsMavenGroup.replace('.', '/')
}
