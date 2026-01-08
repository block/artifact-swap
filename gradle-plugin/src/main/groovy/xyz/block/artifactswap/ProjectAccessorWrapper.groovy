package xyz.block.artifactswap

/**
 * A wrapper around Gradle's project accessor objects that tracks the access path per-instance.
 *
 * This wrapper solves the problem of partially included project hierarchies. For example, when
 * `afterpayApplet.presenters` is included but `afterpayApplet.applets` is excluded:
 * - `projects.afterpayApplet` returns this wrapper around the real `AfterpayAppletProjectDependency`
 * - `.presenters` delegates to the real accessor (returns the included project)
 * - `.applets` returns a `ProjectAccessorDelegate` (resolves to the published artifact)
 *
 * Unlike metaclass modifications (which are per-class and shared across all instances), this wrapper
 * maintains path segments per-instance, allowing the same accessor class to be used at different
 * points in the hierarchy with correct path tracking.
 *
 * The wrapper implements CharSequence so it can be used directly as a dependency when the
 * intermediate accessor is used without further navigation (e.g., `projects.treehouseAndroid`
 * where treehouse-android is both a project and has sub-projects).
 */
class ProjectAccessorWrapper implements GroovyInterceptable, CharSequence {
  private final Object wrapped
  private final String artifactsGroup
  private final List<String> pathSegments
  @Delegate private final ProjectAccessorDelegate selfDelegate

  ProjectAccessorWrapper(Object wrapped, String artifactsGroup, List<String> pathSegments) {
    this.wrapped = wrapped
    this.artifactsGroup = artifactsGroup
    this.pathSegments = pathSegments
    // Create a delegate for when this wrapper is used directly as a dependency
    this.selfDelegate = new ProjectAccessorDelegate(artifactsGroup, pathSegments)
  }

  /**
   * Intercepts all method calls and delegates to the wrapped object.
   */
  def invokeMethod(String name, Object args) {
    return wrapped.invokeMethod(name, args)
  }

  /**
   * Intercepts all property access.
   * - For existing properties: returns a new wrapper with updated path segments, or an artifact delegate for terminals
   * - For missing properties: returns a ProjectAccessorDelegate for artifact resolution
   */
  def getProperty(String name) {
    // Handle special properties that should be accessed directly on wrapper
    if (name == 'wrapped' || name == 'artifactsGroup' || name == 'pathSegments' || name == 'selfDelegate') {
      return this.@"$name"
    }

    def newPathSegments = pathSegments + [name]

    try {
      // Use Groovy's metaclass to dynamically access the property on the wrapped object
      def result = wrapped.metaClass.getProperty(wrapped, name)
      if (result == null) {
        return null
      }

      // If the result is an intermediate accessor (has sub-projects), wrap it
      if (isIntermediateAccessor(result)) {
        return new ProjectAccessorWrapper(result, artifactsGroup, newPathSegments)
      }

      // Terminal/leaf accessor - wrap it so it can be used as dependency but still access metadata
      return new TerminalAccessorWrapper(result, artifactsGroup, newPathSegments)
    } catch (MissingPropertyException e) {
      // Property doesn't exist - return a delegate that resolves to the artifact
      return new ProjectAccessorDelegate(artifactsGroup, newPathSegments)
    }
  }

  /**
   * Checks if an accessor is an intermediate (non-leaf) project accessor.
   * Intermediate accessors are containers that have sub-projects and should be wrapped.
   *
   * In Gradle's generated project accessors:
   * - Intermediate accessors have class names like "FooProjectDependency" (no underscore before last segment)
   * - Terminal/leaf accessors have class names like "Parent_ChildProjectDependency" (underscore indicates nesting)
   *
   * We detect intermediate accessors by checking if they have any getter methods that return
   * other accessor types (ending in ProjectDependency).
   */
  private static boolean isIntermediateAccessor(Object accessor) {
    if (accessor == null) {
      return false
    }

    // Basic types are never intermediate accessors
    if (accessor instanceof CharSequence ||
        accessor instanceof Number ||
        accessor instanceof Boolean ||
        accessor.getClass().isPrimitive()) {
      return false
    }

    def clazz = accessor.getClass()
    def className = clazz.name

    // Must be in the Gradle accessors package
    if (!className.startsWith('org.gradle.accessors.dm.')) {
      return false
    }

    // Check if the class has any getter methods that return other accessor types
    // This indicates it's an intermediate accessor with sub-projects
    def methods = clazz.methods
    for (def method : methods) {
      def methodName = method.name
      // Look for getter methods (get*) that return accessor types
      if (methodName.startsWith('get') && methodName.length() > 3) {
        def returnType = method.returnType
        if (returnType.name.startsWith('org.gradle.accessors.dm.') &&
            returnType.name.endsWith('ProjectDependency')) {
          // This accessor has sub-accessors, so it's intermediate
          return true
        }
      }
    }

    // No sub-accessor getters found, so it's a terminal accessor
    return false
  }

  @Override
  String toString() {
    return selfDelegate.toString()
  }
}
