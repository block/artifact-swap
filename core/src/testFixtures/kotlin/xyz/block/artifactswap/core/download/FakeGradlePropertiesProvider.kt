package xyz.block.artifactswap.core.download

import xyz.block.artifactswap.core.gradle.GradlePropertiesProvider

class FakeGradlePropertiesProvider : GradlePropertiesProvider {
  private val properties = mutableMapOf<String, String>()

  fun setProperty(key: String, value: String) {
    properties[key] = value
  }

  fun clearProperty(key: String) {
    properties.remove(key)
  }

  fun clearAllProperties() {
    properties.clear()
  }

  override fun get(key: String): String {
    return properties[key] ?: "1.2.3"
  }

  override fun getOrNull(key: String): String? {
    // Return default value "1.2.3" if no property set, simulating a configured environment
    // Tests that want to test missing properties should call clearProperty/clearAllProperties
    return properties.getOrDefault(key, "1.2.3")
  }
}
