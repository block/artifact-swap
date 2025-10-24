package xyz.block.artifactswap.core.config

/**
 * Central configuration for all artifact swap operations.
 *
 * This class contains all configurable values that were previously hardcoded with
 * Square/Block-specific values. By centralizing these values, the artifact swap
 * system can be adapted to work with different organizations, repositories, and
 * artifact management systems.
 */
data class ArtifactSwapConfig(
    // ============================================================================
    // Repository Configuration
    // ============================================================================

    /**
     * Name of the primary Artifactory/Maven repository containing built artifacts.
     * This is used as the repository name in Artifactory URLs.
     *
     * Example: "android-register-sandbags" or "my-company-artifacts"
     */
    val primaryRepositoryName: String = "artifact-swap-demo",

    /**
     * Name of the public Artifactory/Maven repository (if separate from primary).
     * Used for artifacts that are publicly accessible, such as protocol buffers.
     *
     * Example: "square-public" or "my-company-public"
     */
    val publicRepositoryName: String = "square-public",

    // ============================================================================
    // Maven Group ID Configuration
    // ============================================================================

    /**
     * Maven group ID for the main artifacts.
     * This is the base group ID used for all internally-built artifacts.
     *
     * Example: "com.squareup.register.sandbags" or "com.mycompany.artifacts"
     */
    val artifactMavenGroup: String = "com.squareup.register.sandbags",

    /**
     * Maven group ID for protocol buffer (proto) artifacts.
     * If your organization uses a separate group for proto definitions.
     *
     * Example: "com.squareup.protos" or "com.mycompany.protos"
     */
    val protosMavenGroup: String = "com.squareup.protos",

    // ============================================================================
    // Maven Repository Path Components
    // ============================================================================

    /**
     * First path segment in the Maven repository structure (typically organization name).
     * Used when constructing file system paths for Maven local repository.
     *
     * For group "com.squareup.register.sandbags", this would be "squareup".
     * The full path becomes: .m2/repository/com/squareup/register/sandbags/...
     *
     * Example: "squareup" or "mycompany"
     */
    val mavenPathOrgSegment: String = "squareup",

    /**
     * Second path segment in the Maven repository structure (typically artifact category).
     * Used when constructing file system paths for Maven local repository.
     *
     * For group "com.squareup.register.sandbags", this would be "sandbags".
     *
     * Example: "sandbags" or "artifacts"
     */
    val mavenPathCategorySegment: String = "sandbags",

    // ============================================================================
    // API Endpoints
    // ============================================================================

    /**
     * Base URL for the production analytics/eventstream API.
     * Used for logging events and telemetry data.
     *
     * Example: "https://api.squareup.com" or "https://analytics.mycompany.com"
     */
    val eventstreamProductionBaseUrl: String = "https://api.squareup.com",

    /**
     * Base URL for the staging analytics/eventstream API.
     * Used for testing event logging before production deployment.
     *
     * Example: "https://api.squareupstaging.com" or "https://analytics-staging.mycompany.com"
     */
    val eventstreamStagingBaseUrl: String = "https://api.squareupstaging.com",

    // ============================================================================
    // Authentication & Credentials
    // ============================================================================

    /**
     * File name of the authentication token for publishing to Artifactory.
     * This file is expected to be in the user's home directory under .artifactory/
     *
     * Example: "ci-worker-android-register-sandbags-publisher-token" or "artifactory-publisher-token"
     */
    val artifactoryPublisherTokenFileName: String = "ci-worker-android-register-sandbags-publisher-token",

    // ============================================================================
    // HTTP Headers
    // ============================================================================

    /**
     * Name of the custom HTTP header for enabling gzip compression in eventstream requests.
     * Set to null to disable the custom header.
     *
     * Example: "X-Square-Gzip" or "X-Custom-Compression"
     */
    val eventstreamGzipHeaderName: String? = "X-Square-Gzip",

    // ============================================================================
    // Gradle Properties
    // ============================================================================

    /**
     * Gradle property key for the generated protos version.
     * Used to specify which version of generated protocol buffer code to use.
     *
     * Example: "square.protosGeneratedVersion" or "mycompany.protosGeneratedVersion"
     */
    val protosGeneratedVersionProperty: String = "square.protosGeneratedVersion",

    /**
     * Gradle property key for the protos schema version.
     * Used to specify which version of the protocol buffer schema definitions to use.
     *
     * Example: "square.protosSchemaVersion" or "mycompany.protosSchemaVersion"
     */
    val protosSchemaVersionProperty: String = "square.protosSchemaVersion",

    /**
     * Gradle property key for enabling local protos (instead of downloading from Maven).
     * When set to true, uses locally built proto artifacts instead of published versions.
     *
     * Example: "square.useLocalProtos" or "mycompany.useLocalProtos"
     */
    val useLocalProtosProperty: String = "square.useLocalProtos",

    /**
     * Gradle property key for the build toolkit plugin flag.
     * Used to conditionally include certain build toolkit functionality.
     *
     * Example: "square.buildToolkitPlugin" or "mycompany.buildToolkitPlugin"
     */
    val buildToolkitPluginProperty: String = "square.buildToolkitPlugin",

    /**
     * Gradle property key for forcing module override behavior.
     * When set, forces the artifact swap plugin to override project dependencies.
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
     * Name of the Gradle settings file for module overrides.
     * This file contains the list of modules to be swapped with published artifacts.
     *
     * Example: "settings_modules_sandbag.gradle" or "settings_artifact_swap.gradle"
     */
    val settingsOverrideFileName: String = "settings_modules_sandbag.gradle",

    /**
     * Relative path to the directory containing cached artifact hashes.
     * Used to track which artifacts have been built locally.
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
    val cliToolDisplayName: String = "sandbagging-tool"
) {
    companion object {
        /**
         * Default configuration using Square/Block internal values.
         * This preserves the original behavior for backward compatibility.
         */
        fun squareDefaults() = ArtifactSwapConfig()

        /**
         * Creates a minimal configuration with only the required fields customized.
         * All other fields use defaults.
         *
         * @param primaryRepositoryName Name of your primary artifact repository
         * @param artifactMavenGroup Maven group ID for your artifacts
         * @param mavenPathOrgSegment Organization segment in Maven paths
         * @param mavenPathCategorySegment Category segment in Maven paths
         */
        fun minimal(
            primaryRepositoryName: String,
            artifactMavenGroup: String,
            mavenPathOrgSegment: String,
            mavenPathCategorySegment: String
        ) = ArtifactSwapConfig(
            primaryRepositoryName = primaryRepositoryName,
            artifactMavenGroup = artifactMavenGroup,
            mavenPathOrgSegment = mavenPathOrgSegment,
            mavenPathCategorySegment = mavenPathCategorySegment
        )
    }
}

/**
 * Global singleton holder for the artifact swap configuration.
 *
 * This allows configuration values to be accessed throughout the codebase without
 * needing to pass the config object through every function call. The instance can
 * be set once at application startup to customize behavior.
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
    var instance: ArtifactSwapConfig = ArtifactSwapConfig()
}
