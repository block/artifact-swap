package xyz.block.artifactswap.idea.util

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.psi.PsiField
import org.jetbrains.android.augment.AndroidLightField
import org.jetbrains.android.augment.ManifestLightField
import org.jetbrains.android.augment.ResourceLightField

/**
 * Support utilities for accessing Android plugin classes when the Android plugin is available.
 *
 * This plugin is built against Android Studio which includes the Android plugin, giving us
 * compile-time type safety with the real Android plugin classes. At runtime, we check if the
 * Android plugin is installed and enabled before attempting to use its classes.
 *
 * The Android plugin dependency is marked as optional in plugin.xml, so Artifact Swap works in both
 * Android Studio (full Android resource navigation) and IntelliJ IDEA (graceful degradation without
 * Android features).
 */
object AndroidPluginSupport {

  private val logger = Logger.getInstance(AndroidPluginSupport::class.java)

  private const val ANDROID_PLUGIN_ID = "org.jetbrains.android"

  /**
   * Checks if the Android plugin is loaded in the current IDE. This is more reliable than catching
   * ClassNotFoundError at runtime.
   */
  private fun isAndroidPluginAvailable(): Boolean {
    return PluginManagerCore.isLoaded(PluginId.getId(ANDROID_PLUGIN_ID))
  }

  /**
   * Information extracted from an Android resource field.
   *
   * @property resourceName The resource name (e.g., "app_name" for R.string.app_name)
   * @property resourceType The resource type (e.g., "string", "drawable")
   */
  data class ResourceInfo(val resourceName: String, val resourceType: String?)

  /**
   * Checks if a field is an Android light field (ResourceLightField, AndroidLightField, etc.)
   *
   * @return true if the field is a recognized Android light field type
   */
  fun isAndroidLightField(field: PsiField): Boolean {
    if (!isAndroidPluginAvailable()) {
      return false
    }

    return try {
      field is AndroidLightField
    } catch (e: Throwable) {
      // Catch any errors during type checking (shouldn't happen if plugin check works)
      logger.debug("Error checking if field is Android light field", e)
      false
    }
  }

  /**
   * Extracts resource information from an Android light field.
   *
   * This method attempts to extract the resource name and type from fields created by the Android
   * plugin.
   *
   * @param field The PSI field to extract information from
   * @return ResourceInfo if extraction succeeds, null otherwise
   */
  fun extractResourceInfo(field: PsiField): ResourceInfo? {
    if (!isAndroidPluginAvailable()) {
      return null
    }

    // Try to cast to ResourceLightField for richer information
    val resourceField = field as? ResourceLightField
    if (resourceField != null) {
      return ResourceInfo(
        resourceName = resourceField.resourceName,
        resourceType = resourceField.resourceType.name.lowercase(),
      )
    }

    // Fall back to AndroidLightField
    if (field is AndroidLightField) {
      return ResourceInfo(
        resourceName = field.name,
        resourceType = field.containingClass.name?.lowercase(),
      )
    }

    return null
  }

  /**
   * Checks if a field is a [ManifestLightField].
   *
   * @param field The field to check
   * @return true if the field is a ManifestLightField
   */
  fun isManifestLightField(field: PsiField): Boolean {
    if (!isAndroidPluginAvailable()) {
      return false
    }

    return field is ManifestLightField
  }
}
