package xyz.block.artifactswap

/**
 * A wrapper/delegate that intercepts project accessor property access and converts excluded
 * projects to artifact dependency notations.
 * 
 * This class serves two purposes:
 * 1. **Wrapper mode** (original != null): Wraps Gradle's generated accessor, tries it first,
 *    falls back to artifact notation if the property throws (excluded project)
 * 2. **Delegate mode** (original == null): Purely builds up artifact notation for chained access
 *    when we've already determined the path is excluded
 * 
 * By implementing CharSequence and delegating to a String, Gradle's dependency handler treats
 * instances of this class as regular dependency notation strings.
 * 
 * Example flow for `projects.account.backend.real`:
 * - `projects` → wrapper with original=RootProjectAccessor
 * - `.account` → tries original, succeeds → wrapper with original=AccountProjectAccessor  
 * - `.backend` → tries original, throws → wrapper with original=null (delegate mode)
 * - `.real` → no original to try → wrapper with original=null, notation="group:account_backend_real"
 */
class ProjectAccessorDelegate implements CharSequence {
  private final Object original  // nullable - null means "delegate mode" (no Gradle accessor to try)
  private final String artifactsGroup
  private final List<String> pathSegments
  @Delegate private final String notation

  ProjectAccessorDelegate(Object original, String artifactsGroup, List<String> pathSegments = []) {
    this.original = original
    this.artifactsGroup = artifactsGroup
    this.pathSegments = pathSegments
    
    // Convert camelCase accessor segments back to kebab-case project names
    // e.g., ['featureFlags', 'api'] -> ['feature-flags', 'api'] -> 'feature-flags_api'
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

  def propertyMissing(String name) {
    return accessProperty(name)
  }

  def getProperty(String name) {
    // Skip internal Groovy properties - return from this instance, not original
    if (name in ['class', 'metaClass']) {
      return this.getClass()."$name"
    }

    return accessProperty(name)
  }

  /**
   * Handle getter method calls like getAccount() in addition to property access.
   * Gradle's generated accessors use getter methods, so we need to intercept these too.
   */
  def methodMissing(String name, def args) {
    // Check if this is a getter method (getXxx with no args)
    if (name.startsWith('get') && name.length() > 3 && (args == null || args.length == 0)) {
      // Convert getter name to property name: getAccount -> account
      def propertyName = name[3].toLowerCase() + name.substring(4)
      return accessProperty(propertyName)
    }

    // For non-getter methods, try to invoke on the original (if we have one)
    if (original != null) {
      return original."$name"(*args)
    }
    throw new MissingMethodException(name, this.class, args as Object[])
  }

  /**
   * Core logic for accessing a property, used by both getProperty and methodMissing.
   */
  private def accessProperty(String name) {
    // Delegate mode: no original to try, just extend the path
    if (original == null) {
      return new ProjectAccessorDelegate(null, artifactsGroup, pathSegments + [name])
    }

    // Wrapper mode: try the original accessor first
    try {
      def result = original."$name"

      // Wrap the result to handle chained access (e.g., projects.account.backend)
      if (result != null) {
        return new ProjectAccessorDelegate(result, artifactsGroup, pathSegments + [name])
      }
      return result
    } catch (MissingPropertyException e) {
      // Property doesn't exist - switch to delegate mode (null original)
      return new ProjectAccessorDelegate(null, artifactsGroup, pathSegments + [name])
    } catch (MissingMethodException e) {
      // Method doesn't exist - switch to delegate mode
      return new ProjectAccessorDelegate(null, artifactsGroup, pathSegments + [name])
    }
  }

  @Override
  String toString() {
    return notation
  }
}
