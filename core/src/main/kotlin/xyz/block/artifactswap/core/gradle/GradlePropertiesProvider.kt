package xyz.block.artifactswap.core.gradle

import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.inputStream

interface GradlePropertiesProvider {
  operator fun get(key: String): String
}

class RealGradlePropertiesProvider(private val propertiesFile: Path) : GradlePropertiesProvider {
  private val gradleProperties = Properties().apply {
    propertiesFile.inputStream().use { load(it) }
  }

  override fun get(key: String): String = gradleProperties[key]?.toString()
    ?: error("$key must be set in $propertiesFile")
}
