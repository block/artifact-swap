package xyz.block.artifactswap.idea.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiField

/**
 * Helper for extracting Android resource information from light fields using reflection.
 *
 * Supports Android plugin synthetic fields defined in [AndroidPluginConstants].
 */
object AndroidResourceHelper {

  private val logger = Logger.getInstance(AndroidResourceHelper::class.java)

  /**
   * Android resource types that are stored in values/ directories (e.g., values/strings.xml,
   * values/colors.xml). Other types like layout and drawable have their own directories.
   */
  val VALUE_RESOURCE_TYPES =
    setOf(
      "string",
      "color",
      "dimen",
      "style",
      "array",
      "plurals",
      "integer",
      "bool",
      "id",
      "attr",
      "styleable",
      "fraction",
    )

  /**
   * Extracts resource information from an Android light field.
   *
   * Returns null if the field is not an Android resource field. Delegates to [AndroidPluginSupport]
   * for safe access to Android plugin classes.
   */
  fun extractResourceInfo(field: PsiField): AndroidPluginSupport.ResourceInfo? {
    return AndroidPluginSupport.extractResourceInfo(field)
  }

  /**
   * Searches for a resource file in a res directory.
   *
   * @param resDir The res directory to search in
   * @param resourceType The type of resource (e.g., "layout", "drawable", "string")
   * @param resourceName The name of the resource (optional, used for non-value resources)
   * @param fileNameToFind The specific filename to find (optional, used for value resources)
   */
  private fun searchInResDirectory(
    resDir: VirtualFile,
    resourceType: String,
    resourceName: String?,
    fileNameToFind: String?,
  ): VirtualFile? {
    if (resourceType in VALUE_RESOURCE_TYPES) {
      // Search in values directories
      val valuesDirs =
        resDir.children
          .filter { child ->
            child.isDirectory && (child.name == "values" || child.name.startsWith("values-"))
          }
          .sortedBy { if (it.name == "values") 0 else 1 }

      for (valuesDir in valuesDirs) {
        if (fileNameToFind != null) {
          val resourceFile = valuesDir.findChild(fileNameToFind)
          if (resourceFile != null) {
            return resourceFile
          }
        }
      }
    } else if (resourceName != null) {
      // For non-value resources, search in type-specific directories
      // Try various qualifiers (e.g., layout, layout-land, drawable-hdpi, etc.)
      val typeDirs =
        resDir.children.filter { child ->
          child.isDirectory &&
            (child.name == resourceType || child.name.startsWith("$resourceType-"))
        }

      for (typeDir in typeDirs) {
        // Find the first file that matches the resource name, regardless of extension
        val resourceFile =
          typeDir.children.firstOrNull { file ->
            !file.isDirectory && file.nameWithoutExtension == resourceName
          }
        if (resourceFile != null) {
          return resourceFile
        }
      }
    }

    return null
  }

  /**
   * Finds a resource file in the module based on resource type and name. For value resources
   * (string, color, etc.), searches in values directories. For other resources (layout, drawable),
   * searches for files with the given resource name in respective directories.
   *
   * @param basePath The base path of the project
   * @param moduleDir The module directory relative to base path
   * @param resourceType The type of resource (e.g., "layout", "drawable", "string")
   * @param resourceName The name of the resource (e.g., "activity_main" for R.layout.activity_main)
   */
  fun findResourceFileByType(
    basePath: String,
    moduleDir: String,
    resourceType: String?,
    resourceName: String?,
  ): VirtualFile? {
    if (resourceType == null) return null

    val localFileSystem = LocalFileSystem.getInstance()
    val moduleRoot = localFileSystem.findFileByPath("$basePath/$moduleDir") ?: return null
    val srcDir = moduleRoot.findChild("src") ?: return null

    val fileNameToFind =
      when {
        resourceType in VALUE_RESOURCE_TYPES -> {
          when (resourceType) {
            "styleable" -> "attrs.xml" // styleables are defined in attrs.xml
            "plurals" -> "plurals.xml" // already plural
            else -> "${resourceType}s.xml" // e.g., strings.xml, colors.xml, dimens.xml
          }
        }
        // For layouts, drawables, etc., use the resource name if provided
        resourceName != null -> "$resourceName.xml" // e.g., activity_main.xml, ic_launcher.xml
        else -> null
      }

    // Try src/main/res first
    val mainRes = localFileSystem.findFileByPath("$basePath/$moduleDir/src/main/res")
    if (mainRes != null && mainRes.isDirectory) {
      val result = searchInResDirectory(mainRes, resourceType, resourceName, fileNameToFind)
      if (result != null) {
        return result
      }
    }

    // Try other source sets
    val otherSourceSets = srcDir.children.filter { it.isDirectory && it.name != "main" }
    for (sourceSet in otherSourceSets) {
      val resDir = sourceSet.findChild("res") ?: continue
      val result = searchInResDirectory(resDir, resourceType, resourceName, fileNameToFind)
      if (result != null) {
        return result
      }
    }

    return null
  }
}
