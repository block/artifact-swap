package xyz.block.artifactswap

/**
 * A wrapper for terminal (leaf) project accessors that:
 * 1. Implements CharSequence so it can be used as a dependency (converts to artifact notation)
 * 2. Delegates property access to the underlying accessor (for metadata like .path, .name, etc.)
 *
 * This is used when a project accessor exists but we want to redirect it to an artifact.
 * Unlike ProjectAccessorDelegate, this wrapper delegates metadata properties to the real accessor
 * instead of treating them as additional path segments.
 */
class TerminalAccessorWrapper implements CharSequence {
  private final Object wrapped
  private final String artifactsGroup
  private final List<String> pathSegments
  @Delegate private final String notation

  TerminalAccessorWrapper(Object wrapped, String artifactsGroup, List<String> pathSegments) {
    this.wrapped = wrapped
    this.artifactsGroup = artifactsGroup
    this.pathSegments = pathSegments

    // Convert camelCase accessor segments back to kebab-case project names
    def convertedSegments = pathSegments.collect { segment ->
      segment.replaceAll(/([A-Z])/, '-$1').toLowerCase()
    }

    String artifactName = convertedSegments.join('_')
    this.notation = "${artifactsGroup}:${artifactName}"
  }

  /**
   * Handles property access by delegating to the underlying accessor.
   * This allows metadata properties like .path, .name to work correctly.
   */
  def propertyMissing(String name) {
    // Delegate to the wrapped accessor's property
    return wrapped.metaClass.getProperty(wrapped, name)
  }

  @Override
  String toString() {
    return notation
  }
}
