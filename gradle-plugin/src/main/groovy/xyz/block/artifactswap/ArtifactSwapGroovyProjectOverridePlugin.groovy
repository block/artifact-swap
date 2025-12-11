package xyz.block.artifactswap

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Groovy-specific plugin that overrides the project() function using metaclass manipulation.
 *
 * The plugin uses Groovy's metaClass to intercept and redirect project() calls to artifact
 * dependencies.
 */
@SuppressWarnings('unused')
class ArtifactSwapGroovyProjectOverridePlugin implements Plugin<Project> {

  @Override
  void apply(Project target) {
    def artifactsGroup = target.providers.gradleProperty("artifactswap.primaryArtifactsMavenGroup").get()
    // This plugin is not applied when artifact sync is inactive
    installProjectOverride(target, artifactsGroup)
  }

  /**
   * Converts a project path to an artifact notation.
   *
   * @param artifactsGroup The project maven group (e.g., 'xyz.block.artifactswap')
   * @param projectPath The project path (e.g., ':common:utils')
   * @return The artifact notation (e.g., 'xyz.block.artifactswap:common_utils')
   */
  private static String toArtifactNotation(String artifactsGroup, String projectPath) {
    String artifactName = projectPath.drop(1).replaceAll(':', '_')
    return "${artifactsGroup}:${artifactName}"
  }

  /**
   * Installs the project() method override using Groovy metaclass manipulation.
   * This directly replaces the project() method on the dependencies handler.
   */
  private static void installProjectOverride(Project project, String artifactsGroup) {
    // Override the project(String) method - this is the main one used in build scripts
    project.dependencies.metaClass.project = { String path ->
      return delegate.create(toArtifactNotation(artifactsGroup, path))
    }

    // Override the project(String, Closure) method for cases with configuration blocks
    project.dependencies.metaClass.project = { String path, Closure configuration ->
      def dependency = delegate.create(toArtifactNotation(artifactsGroup, path))
      if (configuration) {
        configuration.delegate = dependency
        configuration.resolveStrategy = DELEGATE_FIRST
        configuration()
      }
      return dependency
    }

    // Override the project(Map) method for map-style project declarations
    project.dependencies.metaClass.project = { Map notation ->
      return delegate.create(toArtifactNotation(artifactsGroup, notation.path))
    }
  }
}
