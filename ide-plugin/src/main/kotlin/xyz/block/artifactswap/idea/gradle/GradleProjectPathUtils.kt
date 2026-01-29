package xyz.block.artifactswap.idea.gradle

/** Utilities for parsing and validating Gradle project paths. */
object GradleProjectPathUtils {
  /** Match projects.xxx.yyy - no trailing \b to allow dots within the accessor */
  val TYPE_SAFE_ACCESSOR_PATTERN = Regex("""\b(projects\.[\w.]+)""")

  /** Checks if a filename is a Gradle build file. */
  fun isGradleBuildFile(filename: String?): Boolean {
    return filename?.endsWith(".gradle") == true || filename?.endsWith(".gradle.kts") == true
  }

  /** Builds a map of type-safe accessor names to their path representation. */
  fun buildAccessorMap(allProjects: Set<GradlePath>): Map<String, GradlePath> {
    return allProjects.associateBy { it.typeSafeAccessorName }
  }

  /** Cleans a type-safe accessor by removing common prefixes and suffixes. */
  fun cleanTypeSafeAccessor(accessor: String): String {
    return accessor
      .removePrefix("projects.")
      .removeSuffix(".dependencyProject") // deprecated in gradle, to be removed in 9.0
      .removeSuffix(".path")
  }

  /**
   * Checks if an accessor is valid - either an exact match or a valid intermediate namespace. For
   * example, "di" is valid if "di.scoping" exists in the map.
   */
  fun isValidAccessor(accessor: String, accessorMap: Map<String, GradlePath>): Boolean {
    // Exact match
    if (accessorMap.containsKey(accessor)) return true

    // Check if it's a valid prefix (intermediate namespace)
    val prefix = "$accessor."
    return accessorMap.keys.any { it.startsWith(prefix) }
  }
}

/**
 * Simple data class representing a Gradle project path. This is a local copy to avoid depending on
 * Spotlight's internal classes.
 */
data class GradlePath(val path: String) {
  /**
   * The type-safe accessor name for this project. E.g., ":feature:account:api" becomes
   * "feature.account.api"
   */
  val typeSafeAccessorName: String
    get() = path.removePrefix(":").replace(":", ".").replace("-", "")
}
