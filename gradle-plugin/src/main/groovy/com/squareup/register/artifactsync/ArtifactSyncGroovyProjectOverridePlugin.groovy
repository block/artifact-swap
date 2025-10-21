package com.squareup.register.artifactsync

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Groovy-specific plugin that overrides the project() function using metaclass manipulation.
 *
 * The plugin uses Groovy's metaClass to intercept and redirect project() calls to artifact
 * dependencies.
 */
@SuppressWarnings('unused')
class ArtifactSyncGroovyProjectOverridePlugin implements Plugin<Project> {

  @Override
  void apply(Project target) {
    // This plugin is not applied when artifact sync is inactive
    installProjectOverride(target)
  }

  /**
   * Converts a project path to an artifact notation.
   *
   * @param projectPath The project path (e.g., ':common:utils')
   * @return The artifact notation (e.g., 'com.squareup.register.sandbags:common_utils')
   */
  private static String toArtifactNotation(String projectPath) {
    String artifactName = projectPath.drop(1).replaceAll(':', '_')
    return "com.squareup.register.sandbags:${artifactName}"
  }

  /**
   * Installs the project() method override using Groovy metaclass manipulation.
   * This directly replaces the project() method on the dependencies handler.
   */
  private static void installProjectOverride(Project project) {
    // Override the project(String) method - this is the main one used in build scripts
    project.dependencies.metaClass.project = { String path ->
      return delegate.create(toArtifactNotation(path))
    }

    // Override the project(String, Closure) method for cases with configuration blocks
    project.dependencies.metaClass.project = { String path, Closure configuration ->
      def dependency = delegate.create(toArtifactNotation(path))
      if (configuration) {
        configuration.delegate = dependency
        configuration.resolveStrategy = DELEGATE_FIRST
        configuration()
      }
      return dependency
    }

    // Override the project(Map) method for map-style project declarations
    project.dependencies.metaClass.project = { Map notation ->
      return delegate.create(toArtifactNotation(notation.path))
    }
  }
}
