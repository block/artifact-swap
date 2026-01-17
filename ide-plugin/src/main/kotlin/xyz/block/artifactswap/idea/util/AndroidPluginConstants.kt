package xyz.block.artifactswap.idea.util

/**
 * Constants for Android plugin class names.
 *
 * These are synthetic field types created by the Android Gradle Plugin that don't have direct
 * source files in the JAR. We use reflection to extract information from them.
 */
object AndroidPluginConstants {
  /** Synthetic field for Android resources (e.g., R.string.app_name) */
  const val RESOURCE_LIGHT_FIELD = "ResourceLightField"

  /** Synthetic field for Android manifest constants */
  const val MANIFEST_LIGHT_FIELD = "ManifestLightField"

  /** Synthetic field for styleable attributes */
  const val STYLEABLE_ATTR_LIGHT_FIELD = "StyleableAttrLightField"

  /** Generic Android light field */
  const val ANDROID_LIGHT_FIELD = "AndroidLightField"

  /** All Android light field class names */
  val ALL_LIGHT_FIELD_TYPES =
    setOf(
      RESOURCE_LIGHT_FIELD,
      MANIFEST_LIGHT_FIELD,
      STYLEABLE_ATTR_LIGHT_FIELD,
      ANDROID_LIGHT_FIELD,
    )
}
