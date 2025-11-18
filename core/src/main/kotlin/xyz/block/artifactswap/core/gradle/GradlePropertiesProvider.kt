package xyz.block.artifactswap.core.gradle

import java.util.Properties

interface GradlePropertiesProvider {
  operator fun get(key: String): String
}

class RealGradlePropertiesProvider(private val properties: Properties) : GradlePropertiesProvider {
  override fun get(key: String): String =
    properties[key]?.toString() ?: error("$key must be set in gradle properties")
}
