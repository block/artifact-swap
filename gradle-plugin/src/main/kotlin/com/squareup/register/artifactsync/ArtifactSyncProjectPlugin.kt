package com.squareup.register.artifactsync

import com.squareup.gradle.generatedProtosVersion
import com.squareup.gradle.protosSchemaVersion
import com.squareup.gradle.services.services
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySubstitution
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.jetbrains.kotlin.util.prefixIfNot

/**
 * Artifact Sync project sub-plugin. This plugin is responsible for performing dependency substitution
 *
 * Do not apply this plugin directly! It is auto-applied by [ArtifactSyncSettingsPlugin].
 * For reference and searchability, the ID of this plugin is `com.squareup.register.artifactsync`.
 */
@Suppress("unused")
class ArtifactSyncProjectPlugin : Plugin<Project> {
  override fun apply(target: Project) = target.run {
    val map = gradle.services.artifactSyncBomService.bomVersionMap
    substituteAllDependencies {
      val requested = requested as? ModuleComponentSelector ?: return@substituteAllDependencies

      // Given a project structure like this:
      //
      // :hobbits
      //   implementation project(':isengard')
      // :isengard
      //   implementation project(':mordor')
      // :mordor
      //
      // If a user requests to sync only `:hobits` and `:mordor`, `:isengard` is replaced by
      // ArtifactSyncSettingsPlugin with a maven reference to `com.squareup.sandbags:isengard`.
      //
      // `com.squareup.sandbags:isengard` will have a dependency on
      //   `com.squareup.sandbags:mordor`, which is a published representation of a project that
      // is currently `include`-ed in the build. This needs to be rewritten via dependency
      // substitution to `project(':mordor')`.
      if (requested.group == ARTIFACT_SYNC_MAVEN_GROUP) {
        when (val p = findProject(requested.asProjectPath)) {
          null -> {
            // Force Gradle to use the BOM specified version for this artifact.
            // Artifacts are only published when their source changes, so their POM references the
            // artifact versions of other projects at the time of publication. Referenced projects
            // may change and be re-published, so these versions contained in the artifact POM may
            // become out-of-date. The Artifact Sync BOM contains the most recent versions of each
            // artifact so we use that as our source of truth.
            useTarget("${requested.group}:${requested.module}:${map[requested.module]}")
          }

          else -> useTarget(p) // If the project exists, substitute the artifact with the project
        }
      } else if (requested.group == PROTOS_MAVEN_GROUP) {
        // When artifact sync is active, the project artifacts may reference old versions of protos.
        // Protos should be force resolved to the current version otherwise it may cause
        // unnecessary downloads of oudated artifacts.
        val version = when (requested.module) {
          // all-protos is the input to the protos generator and is versioned separately
          "all-protos" -> protosSchemaVersion
          else -> generatedProtosVersion
        }
        useTarget("${requested.group}:${requested.module}:$version")
      }

      // TODO: In the future, we should rewrite all of the dependencies of artifacts to the
      //  current versions used by the build as specified by dependencies.gradle, or in the
      //  future, the version catalog
    }
  }
}

/**
 * The Gradle DSL for this is obnoxiously deeply nested, so just abstract it into a simple wrapper DSL
 */
private fun Project.substituteAllDependencies(substitution: DependencySubstitution.() -> Unit) {
  configurations.configureEach { configs ->
    if (configs.isCanBeResolved) {
      configs.resolutionStrategy { resolution ->
        resolution.dependencySubstitution { subs ->
          subs.all substitution@{ dep ->
            dep.substitution()
          }
        }
      }
    }
  }
}

/**
 * This reverses the project -> maven dependency notation rewriting performed by
 * [ArtifactSyncSettingsPlugin].
 */
private val ModuleComponentSelector.asProjectPath: String
  get() = module.replace("_", ":").prefixIfNot(":")
