@file:Suppress("UnstableApiUsage")

package xyz.block.artifactswap

import xyz.block.gradle.LOCAL_PROTOS_ARTIFACTS
import xyz.block.gradle.bomVersion
import xyz.block.gradle.useArtifactSync
import xyz.block.gradle.useLocalProtos
import xyz.block.ide.forceSettingsModulesOverride
import xyz.block.ide.isIdeSync
import xyz.block.gradle.services.services
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.resolve.DependencyResolutionManagement
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.io.File

/**
 * Main Artifact Sync settings plugin. This plugin is responsible for:
 * 1. Applying the right settings_modules_*.gradle file to `include` projects (even when artifact sync
 *    is not used)
 * 2. Mangling build files to redirect `project()` references to artifacts
 * 3. Setting up the local maven repo where those artifacts may be found
 * 4. Applying [ArtifactSwapProjectPlugin] to each project
 *
 * plugins {
 *   id 'com.squareup.register.artifactsync.settings'
 * }
 */
@Suppress("unused")
class ArtifactSwapSettingsPlugin : Plugin<Settings> {
  override fun apply(target: Settings) = target.run {
    applyProjectIncludes()
    maybeApplyArtifactSync()
    maybeUseLocalProtos()
  }

  private fun Settings.applyProjectIncludes() {
    val includesFile = getProjectIncludesFile()
    logger.lifecycle("Including projects from {}", includesFile.relativeTo(rootDir))
    settings.apply { it.from(includesFile) }
  }

  private val Settings.allSettingsFile
    get() = File(rootDir, GRADLE_SETTINGS_MODULES_ALL)
  private val Settings.settingsOverrideFile
    get() = File(rootDir, GRADLE_SETTINGS_OVERRIDE)
  private val Settings.artifactSyncOverrideFile
    get() = File(rootDir, GRADLE_SETTINGS_OVERRIDE_ARTIFACT_SYNC)

  /**
   * Determines the Gradle settings plugin script to apply project inclusions from.
   * This used to live in main settings.gradle
   *
   * TODO: instead of applying a gradle script to include projects, write the project list to a flat
   *  file and read it:
   *    rootDir.toPath().resolve(".gradle/sandbagHashes/paths.txt")
   *      .readLines()
   *      .forEach { projectPath -> include(projectPath) }
   */
  private fun Settings.getProjectIncludesFile(): File {
    // We only apply settings override files if doing Gradle Sync (to trim out unneeded projects) or
    // if explicitly requested.
    return if (isIdeSync || forceSettingsModulesOverride) {
      if (isIdeSync && useArtifactSync) {
        // Artifact sync currently only applies to gradle sync and not to builds
        if (artifactSyncOverrideFile.exists()) {
          artifactSyncOverrideFile
        } else {
          logSlowSyncWarning()
          allSettingsFile
        }
      } else if (settingsOverrideFile.exists()) {
        // However, we still want the sync mechanism to use the override file, so we need to
        // keep that logic in place to maintain existing behavior.
        settingsOverrideFile
      } else {
        // Would normally apply an override file, but no applicable override file was generated
        logSlowSyncWarning()
        allSettingsFile
      }
    } else {
      // For CLI we don't need to override anything, configure on demand is doing this for us.
      allSettingsFile
    }
  }

  private fun Settings.logSlowSyncWarning() {
    if (isIdeSync) {
      logger.error(
        "Syncing the entire repo is very slow! " +
          "Use `./setupIdeModules` to select only the parts of the repo you need to sync."
      )
    }
  }

  private fun Settings.maybeApplyArtifactSync() {
    // When using artifact sync, all project dependencies are converted to artifacts
    // This plugin replaces the artifacts with the actual project dependencies if they are found in
    // the build
    if (isIdeSync && useArtifactSync && artifactSyncOverrideFile.exists()) {
      logger.warn("Using Artifact Sync! See https://go/artifact-sync for docs.")
      gradle.settingsEvaluated {
        // This has to run after settings are evaluated. It ensures that the project descriptor
        // registry contains the full list of projects that are `include`-ed by the applied settings
        // script, that the artifact sync BOM version is present in the `ext` properties.
        logger.debug("Artifact Sync BOM version: {}", bomVersion)

        // Service should only be registered and retrieved when using artifact sync and during an
        // IDE sync
        gradle.services.register(ArtifactSwapBomService.KEY, ArtifactSwapBomService::class.java) {
          it.parameters.bomVersion.set(bomVersion)
        }
      }

      dependencyResolutionManagement.setupArtifactRepository()
      gradle.lifecycle.beforeProject {
        // Groovy metaprogramming to rewrite project() references
        // Kotlin source can't reference the groovy class so we apply by ID instead.
        it.plugins.apply("xyz.block.artifactswap.groovy-override")
        // Apply sub-plugin to all projects that swaps artifact references back to gradle projects
        // if applicable.
        it.plugins.apply(ArtifactSwapProjectPlugin::class.java)
      }
    } else {
      logger.debug("Artifact Sync is inactive.")
    }
  }

  /**
   * The artifact sync downloader tool runs on git checkout and similar hooks to pre-fetch the jars
   * and aar files for artifact sync in an optimized way. We force Gradle to only look for these in
   * maven local to avoid having Gradle waste time searching for or refreshing metadata for these from
   * remote repos where we already know they don't exist.
   */
  private fun DependencyResolutionManagement.setupArtifactRepository() {
    repositories.let { repos ->
      repos.exclusiveContent { ex ->
        ex.forRepositories(repos.mavenLocal())
        ex.filter { config ->
          config.includeGroup(ARTIFACT_SWAP_MAVEN_GROUP)
        }
      }
    }
  }

  private fun Settings.maybeUseLocalProtos() {
    if (useArtifactSync || useLocalProtos) {
      logger.warn(
        "Using locally synced protos artifacts! " +
          "If you have issues set $LOCAL_PROTOS_ARTIFACTS=false"
      )
      dependencyResolutionManagement.setupLocalProtosRepository()
    } else {
      logger.debug("Not using locally synced protos artifacts")
    }
  }

  /**
   * The artifact sync downloader tool runs on git checkout and similar hooks to pre-fetch the jars
   * for protos dependencies in an optimized way. We force Gradle to only look for these in maven
   * local to avoid having Gradle waste time searching for or refreshing metadata for these from
   * remote repos.
   */
  private fun DependencyResolutionManagement.setupLocalProtosRepository() {
    repositories.let { repos ->
      repos.exclusiveContent { ex ->
        ex.forRepositories(repos.mavenLocal())
        ex.filter { config ->
          config.includeGroup("com.squareup.protos")
        }
      }
    }
  }

  private companion object {
    val logger: Logger = Logging.getLogger(ArtifactSwapSettingsPlugin::class.java)

    /** This is the default settings.gradle file containing ALL projects in our build */
    const val GRADLE_SETTINGS_MODULES_ALL = "settings_modules_all.gradle"

    /** This is the settings.gradle file generated by setupIdeModules */
    const val GRADLE_SETTINGS_OVERRIDE = "settings_modules_override.gradle"

    /**
     * Artifact sync override file, also generated by setupIdeModules
     * TODO: The filename here is remnant of original project codenames, we should change it.
     *   WTF is a sandbag
     */
    const val GRADLE_SETTINGS_OVERRIDE_ARTIFACT_SYNC = "settings_modules_sandbag.gradle"
  }
}
