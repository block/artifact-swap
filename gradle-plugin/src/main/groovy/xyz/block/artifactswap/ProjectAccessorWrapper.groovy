package xyz.block.artifactswap

/**
 * A proxy for Gradle project accessors that converts all project references to artifact dependencies.
 *
 * All project references are converted to artifacts. The dependency substitution plugin
 * (ArtifactSwapProjectPlugin) handles converting included projects back to actual Gradle projects.
 *
 * Implements CharSequence so it can be used directly as a dependency notation.
 */
class ProjectAccessorWrapper implements CharSequence {
  /** The wrapped Gradle accessor, or null if the accessor doesn't exist */
  private final Object wrapped
  private final String artifactsGroup
  private final List<String> pathSegments

  // Lazily computed artifact notation
  private String _notation

  ProjectAccessorWrapper(Object wrapped, String artifactsGroup, List<String> pathSegments) {
    this.wrapped = wrapped
    this.artifactsGroup = artifactsGroup
    this.pathSegments = pathSegments
  }

  /**
   * Gets the artifact notation for this accessor path.
   * Converts camelCase accessor segments back to kebab-case project names.
   */
  String getNotation() {
    if (_notation == null) {
      def convertedSegments = pathSegments.collect { segment ->
        segment.replaceAll(/([A-Z])/, '-$1').toLowerCase()
      }
      String artifactName = convertedSegments.join('_')
      _notation = "${artifactsGroup}:${artifactName}"
    }
    return _notation
  }

  /**
   * Handles property access on this proxy.
   */
  def propertyMissing(String name) {
    return getPropertyInternal(name)
  }

  /**
   * Internal property access implementation.
   */
  private def getPropertyInternal(String name) {
    def newPath = pathSegments + [name]

    // If we have no wrapped accessor, all property access creates new proxies
    if (wrapped == null) {
      return new ProjectAccessorWrapper(null, artifactsGroup, newPath)
    }

    try {
      // Try to get the property from the wrapped accessor
      def result = wrapped.metaClass.getProperty(wrapped, name)
      if (result == null) {
        return null
      }

      // If result is a Gradle accessor, wrap it
      if (isGradleAccessor(result)) {
        return new ProjectAccessorWrapper(result, artifactsGroup, newPath)
      }

      // Otherwise return the raw value (metadata like .path, .name)
      return result
    } catch (MissingPropertyException e) {
      // Property doesn't exist - return proxy for artifact resolution
      return new ProjectAccessorWrapper(null, artifactsGroup, newPath)
    }
  }

  /**
   * Checks if an object is a Gradle-generated project accessor.
   */
  private static boolean isGradleAccessor(Object obj) {
    if (obj == null) return false
    def className = obj.getClass().name
    return className.startsWith('org.gradle.accessors.dm.')
  }

  // CharSequence implementation - delegates to artifact notation
  @Override
  int length() { return getNotation().length() }

  @Override
  char charAt(int index) { return getNotation().charAt(index) }

  @Override
  CharSequence subSequence(int start, int end) { return getNotation().subSequence(start, end) }

  @Override
  String toString() { return getNotation() }
}
