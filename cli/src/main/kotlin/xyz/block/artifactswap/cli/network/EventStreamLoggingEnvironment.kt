package xyz.block.artifactswap.cli.network

/**
 * Enum for configuring the ES2 logging environment
 */
enum class EventStreamLoggingEnvironment(val baseUrl: String) {
    STAGING("https://api.squareupstaging.com"),
    PRODUCTION("https://api.squareup.com");
}
