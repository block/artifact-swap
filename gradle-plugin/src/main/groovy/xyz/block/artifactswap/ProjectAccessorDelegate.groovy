package xyz.block.artifactswap

/**
 * A dynamic delegate that handles chained project accessor calls (e.g., projects.di.scoping) and
 * converts them to artifact dependencies when projects are excluded from the build.
 *
 * When Artifact Swap excludes a project from the build, Gradle's generated type-safe project
 * accessors (e.g., projects.di.scoping) throw "unknown property" errors because those accessors
 * are only generated for projects that are included in settings.gradle. However, buildscripts may
 * still reference these projects via the type-safe accessors.
 *
 * This delegate intercepts property access on the projects extension for missing (excluded)
 * projects and returns a dependency notation string (e.g., "com.squareup.cash.artifacts:di_scoping")
 * instead. This allows excluded project references to resolve to their published artifact equivalents.
 *
 * By implementing CharSequence and delegating to a String, Gradle's dependency handler treats
 * instances of this class as regular dependency notation strings. The propertyMissing method
 * enables chaining (e.g., projects.di.scoping becomes "di_scoping" artifact).
 *
 * Note: This only handles Groovy buildscripts.
 */
class ProjectAccessorDelegate implements CharSequence {
  private final String artifactsGroup
  private final List<String> pathSegments
  @Delegate private final String notation

  ProjectAccessorDelegate(String artifactsGroup, List<String> pathSegments) {
    this.artifactsGroup = artifactsGroup
    this.pathSegments = pathSegments
    
    // Convert camelCase accessor segments back to kebab-case project names
    // e.g., ['featureFlags', 'api'] -> [':feature-flags', 'api'] -> 'feature-flags_api'
    // Project accessor name generation is lossy, so to avoid poking around the multiple possible
    // options on disk to find the right one, we make an assumption about the naming convention.
    // This is the same assumption that Spotlight makes for STRICT generated project accessors.
    // https://github.com/joshfriend/spotlight/blob/v1.4.1/buildscript-utils/src/main/kotlin/com/fueledbycaffeine/spotlight/buildscript/BuildFile.kt#L22-L26
    def convertedSegments = pathSegments.collect { segment ->
      // Insert hyphen before uppercase letters and lowercase the result
      segment.replaceAll(/([A-Z])/, '-$1').toLowerCase()
    }
    
    String artifactName = convertedSegments.join('_')
    this.notation = "${artifactsGroup}:${artifactName}"
  }

  /**
   * Handles nested property access (e.g., projects.di.scoping accesses 'di' then 'scoping').
   * https://groovy-lang.org/metaprogramming.html#_propertymissing
   */
  def propertyMissing(String name) {
    return new ProjectAccessorDelegate(artifactsGroup, pathSegments + [name])
  }
  
  @Override
  String toString() {
    return notation
  }
}
