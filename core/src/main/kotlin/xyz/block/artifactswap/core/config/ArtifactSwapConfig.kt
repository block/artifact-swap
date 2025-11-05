package xyz.block.artifactswap.core.config

import org.apache.logging.log4j.kotlin.logger
import java.io.File
import java.util.Properties

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
  // HTTP Headers
  // ============================================================================

  /**
   * Name of the custom HTTP header for enabling gzip compression in eventstream requests. Set to
   * null to disable the custom header.
   *
   * Example: "X-MyCompany-Gzip" or null to disable
   */
  val eventstreamGzipHeaderName: String? = "X-MyCompany-Gzip",

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

  /**
   * Gradle property key for enabling local protos (instead of downloading from Maven). When set to
   * true, uses locally built proto artifacts instead of published versions.
   *
   * Example: "square.useLocalProtos" or "mycompany.useLocalProtos"
   */
  val useLocalProtosProperty: String = "square.useLocalProtos",

  /**
   * Gradle property key for the build toolkit plugin flag. Used to conditionally include certain
   * build toolkit functionality.
   *
   * Example: "square.buildToolkitPlugin" or "mycompany.buildToolkitPlugin"
   */
  val buildToolkitPluginProperty: String = "square.buildToolkitPlugin",

  /**
   * Gradle property key for forcing module override behavior. When set, forces the artifact swap
   * plugin to override project dependencies.
   *
   * Example: "square.force.modules.override" or "mycompany.force.modules.override"
   */
  val forceModulesOverrideProperty: String = "square.force.modules.override",

  /**
   * Gradle extra property key for the artifact version (used in build scripts).
   *
   * Example: "sandbagVersion" or "artifactVersion"
   */
  val artifactVersionExtraProperty: String = "sandbagVersion",

  // ============================================================================
  // File and Directory Names
  // ============================================================================

  /**
   * Name of the Gradle settings file for module overrides. This file contains the list of modules
   * to be swapped with published artifacts.
   *
   * Example: "settings_modules_sandbag.gradle" or "settings_artifact_swap.gradle"
   */
  val settingsOverrideFileName: String = "settings_modules_sandbag.gradle",

  /**
   * Relative path to the directory containing cached artifact hashes. Used to track which artifacts
   * have been built locally.
   *
   * Example: ".gradle/sandbagHashes/paths.txt" or ".gradle/artifact-cache/hashes.txt"
   */
  val artifactHashCachePath: String = ".gradle/sandbagHashes/paths.txt",

  // ============================================================================
  // Display Names
  // ============================================================================

  /**
   * Display name for the CLI tool shown in help text and version info.
   *
   * Example: "sandbagging-tool" or "artifact-swap-tool"
   */
  val cliToolDisplayName: String = "sandbagging-tool",

  // ============================================================================
  // Gradle Projects Settings
  // ============================================================================

  /** List of Gradle project paths to exclude from artifact swapping. */
  val excludeGradleProjects: List<String> = emptyList(),

  // ============================================================================
  // Artifactory
  // ============================================================================
  val artifactoryBaseUrl: String = "https://artifactory.example.com",
) {
  val primaryArtifactsMavenGroupArtifactoryPath = primaryArtifactsMavenGroup.replace('.', '/')
}

// Eventstream Gzip Header
const val EVENTSTREAM_GZIP_HEADER = "X-Square-Gzip: true"
const val EVENTSTREAM_LOG_EVENTS_PATH = "/demo/path"

/**
 * Global singleton holder for the artifact swap configuration.
 *
 * This allows configuration values to be accessed throughout the codebase without needing to pass
 * the config object through every function call. The instance can be set once at application
 * startup to customize behavior.
 *
 * Usage:
 * ```
 * // At application startup (e.g., in main())
 * ArtifactSwapConfigHolder.instance = ArtifactSwapConfig.minimal(
 *     primaryRepositoryName = "my-artifacts",
 *     artifactMavenGroup = "com.mycompany.artifacts",
 *     mavenPathOrgSegment = "mycompany",
 *     mavenPathCategorySegment = "artifacts"
 * )
 *
 * // Anywhere in the code
 * val repoName = ArtifactSwapConfigHolder.instance.primaryRepositoryName
 * ```
 */
object ArtifactSwapConfigHolder {
    /**
     * The current configuration instance. Defaults to Square/Block values.
     * Can be reassigned at runtime to customize behavior.
     */
    val instance: ArtifactSwapConfig = createFromGradleProperties()

    /**
     * Creates an ArtifactSwapConfig by reading values from gradle.properties files.
     *
     * Properties are loaded with the following precedence (highest to lowest priority):
     * 1. ~/.gradle/gradle.properties (global user settings - highest priority)
     * 2. $pwd/gradle.properties (project-specific settings)
     * 3. Default values defined in ArtifactSwapConfig class (lowest priority)
     *
     * This means global settings in your home directory will override project-specific
     * settings, which in turn override the defaults.
     *
     * Supported properties:
     * - artifactswap.primaryRepositoryName
     * - artifactswap.secondaryRepositoryName
     * - artifactswap.primaryArtifactsMavenGroup
     * - artifactswap.secondaryArtifactsMavenGroup
     * - artifactswap.eventstreamBaseUrl
     * - artifactswap.artifactoryPublisherTokenFileName
     * - artifactswap.eventstreamGzipHeaderName
     * - artifactswap.protosGeneratedVersionProperty
     * - artifactswap.protosSchemaVersionProperty
     * - artifactswap.useLocalProtosProperty
     * - artifactswap.buildToolkitPluginProperty
     * - artifactswap.forceModulesOverrideProperty
     * - artifactswap.artifactVersionExtraProperty
     * - artifactswap.settingsOverrideFileName
     * - artifactswap.artifactHashCachePath
     * - artifactswap.cliToolDisplayName
     * - artifactswap.artifactoryBaseUrl
     *
     * Falls back to default values for any properties not specified.
     */
    private fun createFromGradleProperties(): ArtifactSwapConfig {
        val userHome = System.getProperty("user.home")
        val globalGradlePropertiesFile = File(userHome, ".gradle/gradle.properties")
        val localGradlePropertiesFile = File(System.getProperty("user.dir"), "gradle.properties")

        // Load properties with precedence: local first, then global (which will override)
        val mergedProperties = Properties()

        if (localGradlePropertiesFile.exists()) {
            logger.info { "Loading ArtifactSwapConfig from local gradle properties: $localGradlePropertiesFile" }
            localGradlePropertiesFile.inputStream().use { mergedProperties.load(it) }
        } else {
            logger.info { "Local gradle properties file does not exist: $localGradlePropertiesFile" }
        }

        if (globalGradlePropertiesFile.exists()) {
            logger.info { "Loading ArtifactSwapConfig from global gradle properties: $globalGradlePropertiesFile" }
            globalGradlePropertiesFile.inputStream().use { mergedProperties.load(it) }
        } else {
            logger.info { "Global gradle properties file does not exist: $globalGradlePropertiesFile" }
        }

        if (!globalGradlePropertiesFile.exists() && !localGradlePropertiesFile.exists()) {
            logger.error { "No gradle properties files found, falling back to default properties" }
            return ArtifactSwapConfig()
        }

        val defaults = ArtifactSwapConfig()

        fun getPropertyWithLogging(key: String, default: String): String {
            val value = mergedProperties.getProperty(key)
            if (value != null) {
                logger.info { "Using property from gradle.properties: $key = $value" }
                return value
            }
            return default
        }

        fun getOptionalPropertyWithLogging(key: String): String? {
            val value = mergedProperties.getProperty(key)
            if (value != null) {
                logger.info { "Using property from gradle.properties: $key = $value" }
            }
            return value
        }

        return ArtifactSwapConfig(
            primaryRepositoryName = getPropertyWithLogging(
                "artifactswap.primaryRepositoryName",
                defaults.primaryRepositoryName
            ),
            secondaryRepositoryName = getPropertyWithLogging(
                "artifactswap.secondaryRepositoryName",
                defaults.secondaryRepositoryName
            ),
            primaryArtifactsMavenGroup = getPropertyWithLogging(
                "artifactswap.primaryArtifactsMavenGroup",
                defaults.primaryArtifactsMavenGroup
            ),
            secondaryArtifactsMavenGroup = getPropertyWithLogging(
                "artifactswap.secondaryArtifactsMavenGroup",
                defaults.secondaryArtifactsMavenGroup
            ),
            eventstreamBaseUrl = getPropertyWithLogging(
                "artifactswap.eventstreamBaseUrl",
                defaults.eventstreamBaseUrl
            ),
            artifactoryPublisherTokenFileName = getPropertyWithLogging(
                "artifactswap.artifactoryPublisherTokenFileName",
                defaults.artifactoryPublisherTokenFileName
            ),
            eventstreamGzipHeaderName = getOptionalPropertyWithLogging("artifactswap.eventstreamGzipHeaderName"),
            protosGeneratedVersionProperty = getPropertyWithLogging(
                "artifactswap.protosGeneratedVersionProperty",
                defaults.protosGeneratedVersionProperty
            ),
            protosSchemaVersionProperty = getPropertyWithLogging(
                "artifactswap.protosSchemaVersionProperty",
                defaults.protosSchemaVersionProperty
            ),
            useLocalProtosProperty = getPropertyWithLogging(
                "artifactswap.useLocalProtosProperty",
                defaults.useLocalProtosProperty
            ),
            buildToolkitPluginProperty = getPropertyWithLogging(
                "artifactswap.buildToolkitPluginProperty",
                defaults.buildToolkitPluginProperty
            ),
            forceModulesOverrideProperty = getPropertyWithLogging(
                "artifactswap.forceModulesOverrideProperty",
                defaults.forceModulesOverrideProperty
            ),
            artifactVersionExtraProperty = getPropertyWithLogging(
                "artifactswap.artifactVersionExtraProperty",
                defaults.artifactVersionExtraProperty
            ),
            settingsOverrideFileName = getPropertyWithLogging(
                "artifactswap.settingsOverrideFileName",
                defaults.settingsOverrideFileName
            ),
            artifactHashCachePath = getPropertyWithLogging(
                "artifactswap.artifactHashCachePath",
                defaults.artifactHashCachePath
            ),
            cliToolDisplayName = getPropertyWithLogging(
                "artifactswap.cliToolDisplayName",
                defaults.cliToolDisplayName
            ),
            artifactoryBaseUrl = getPropertyWithLogging(
                "artifactswap.artifactoryBaseUrl",
                defaults.artifactoryBaseUrl
            ),
        )
    }
}
