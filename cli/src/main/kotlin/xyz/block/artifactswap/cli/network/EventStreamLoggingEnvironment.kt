package xyz.block.artifactswap.cli.network

/**
 * Enum for configuring the environment to which event stream logs are sent.
 */
enum class EventStreamLoggingEnvironment(val baseUrl: String) {
    STAGING("https://staging.example.com"),
    PRODUCTION("https://prod.example.com"),;
}
