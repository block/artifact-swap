@file:Suppress("UnstableApiUsage")

package xyz.block.artifactswap

import com.fueledbycaffeine.spotlight.SpotlightSettingsPlugin
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.resolve.DependencyResolutionManagement
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import xyz.block.gradle.ARTIFACT_SWAP_ENABLED
import xyz.block.gradle.ArtifactSwapDependencyHandler
import xyz.block.gradle.LOCAL_PROTOS_ARTIFACTS
import xyz.block.gradle.hasPublishableComponent
import xyz.block.gradle.isArtifactPublishingEnabled
import xyz.block.gradle.services.services
import xyz.block.gradle.useArtifactSwap
import xyz.block.gradle.useLocalProtos
import xyz.block.ide.isIdeSync

/**
 * Main Artifact Sync settings plugin. This plugin is responsible for:
 * 1. Applying the right settings_modules_*.gradle file to `include` projects (even when artifact
 *    sync is not used)
 * 2. Mangling build files to redirect `project()` references to artifacts
 * 3. Setting up the local maven repo where those artifacts may be found
 * 4. Applying [ArtifactSwapProjectPlugin] to each project
 *
 * ```
 * plugins {
 *   id 'xyz.block.artifactswap.settings'
 * }
 * ```
 */
@Suppress("unused")
public class ArtifactSwapSettingsPlugin : Plugin<Settings> {

  public override fun apply(target: Settings): Unit =
    target.run {
      val artifactSwapIsActive = isIdeSync && useArtifactSwap
      when {
        artifactSwapIsActive -> applyArtifactSwap()
        else -> applySpotlight()
      }
      setupKtsDependencyHandlerOverride(artifactSwapIsActive)
      maybeApplyPublishPlugin()
      maybeUseLocalProtos()
    }

  private fun Settings.applyArtifactSwap() {
    logger.lifecycle("Using Artifact Swap! See https://go/artifact-sync for docs.")
    logger.lifecycle(
      "You can disable this by setting $ARTIFACT_SWAP_ENABLED=false in your gradle properties"
    )
    gradle.settingsEvaluated {
      val selectionResult = selectProjectsForArtifactSwap()
      selectionResult.selectedProjects.forEach { include(it) }
      setupArtifactSwapInfrastructure(selectionResult.bomVersion)
    }
  }

  private fun Settings.applySpotlight() {
    logger.info("Artifact Sync is inactive. Delegating to Spotlight.")
    pluginManager.apply(SpotlightSettingsPlugin::class.java)
  }

  /**
   * Select projects for artifact swap based on:
   * 1. User's requested projects (from setupIdeModules)
   * 2. Projects with local changes (from git)
   * 3. Projects without downloaded artifacts
   * 4. Transitive dependencies (from spotlight graph)
   *
   * Uses a ValueSource to encapsulate all I/O operations for configuration cache compatibility.
   */
  private fun Settings.selectProjectsForArtifactSwap():
    ArtifactSwapModuleSelectionValueSource.Result {
    return providers
      .of(ArtifactSwapModuleSelectionValueSource::class.java) {
        it.parameters.rootDirectory.set(settingsDir)
        it.parameters.rootProjectName.set(rootProject.name)
        it.parameters.config.set(artifactSwapConfig)
      }
      .get()
  }

  /**
   * Set up the artifact swap infrastructure:
   * 1. Register BOM version service
   * 2. Configure maven local repository for artifacts
   * 3. Apply artifact swap plugins to all projects
   */
  private fun Settings.setupArtifactSwapInfrastructure(bomVersion: String) {
    logger.info("Artifact Swap BOM version: {}", bomVersion)
    gradle.services.register(ArtifactSwapBomService.KEY, ArtifactSwapBomService::class.java) {
      it.parameters.bomVersion.set(bomVersion)
      it.parameters.artifactSwapMavenGroup.set(artifactSwapConfig.primaryArtifactsMavenGroup)
    }

    // Force Gradle to only look for swapped artifacts in maven local
    dependencyResolutionManagement.setupArtifactRepository(
      artifactSwapConfig.primaryArtifactsMavenGroup
    )

    // Apply artifact swap plugins to all projects
    gradle.lifecycle.beforeProject {
      // Groovy metaprogramming to rewrite project() references
      // Kotlin source can't reference the groovy class so we apply by ID instead.
      it.plugins.apply("xyz.block.artifactswap.groovy-override")
      // Apply sub-plugin to all projects that swaps artifact references back to gradle projects
      // if applicable.
      it.plugins.apply(ArtifactSwapProjectPlugin::class.java)
    }
  }

  private fun Settings.setupKtsDependencyHandlerOverride(artifactSwapIsActive: Boolean) {
    // We need to do this for all builds, because our new DependencyHandler.project() function
    // will ALWAYS be on the classpath (visible to build scripts).
    gradle.lifecycle.beforeProject { p ->
      ArtifactSwapDependencyHandler.create(p, artifactSwapIsActive)
    }
  }

  /** Applies the publish plugin to all projects when sandbag publishing is enabled. */
  private fun Settings.maybeApplyPublishPlugin() {
    if (isArtifactPublishingEnabled) {
      gradle.lifecycle.beforeProject { project -> project.plugins.apply("maven-publish") }
      gradle.lifecycle.afterProject { project ->
        if (project.hasPublishableComponent) {
          project.plugins.apply(ArtifactSwapProjectPublishPlugin::class.java)
        }
      }
    }
  }

  /**
   * The artifact sync downloader tool runs on git checkout and similar hooks to pre-fetch the jars
   * and aar files for artifact sync in an optimized way. We force Gradle to only look for these in
   * maven local to avoid having Gradle waste time searching for or refreshing metadata for these
   * from remote repos where we already know they don't exist.
   */
  private fun DependencyResolutionManagement.setupArtifactRepository(
    artifactSwapMavenGroupId: String
  ) {
    repositories.let { repos ->
      repos.exclusiveContent { ex ->
        ex.forRepositories(repos.mavenLocal())
        ex.filter { config -> config.includeGroup(artifactSwapMavenGroupId) }
      }
    }
  }

  private fun Settings.maybeUseLocalProtos() {
    if (useArtifactSwap || useLocalProtos) {
      logger.warn(
        "Using locally synced protos artifacts! " +
          "If you have issues set $LOCAL_PROTOS_ARTIFACTS=false"
      )
      dependencyResolutionManagement.setupLocalProtosRepository()
    } else {
      logger.info("Not using locally synced protos artifacts")
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
        ex.filter { config -> config.includeGroup("com.squareup.protos") }
      }
    }
  }

  private companion object {
    val logger: Logger = Logging.getLogger(ArtifactSwapSettingsPlugin::class.java)
  }
}
