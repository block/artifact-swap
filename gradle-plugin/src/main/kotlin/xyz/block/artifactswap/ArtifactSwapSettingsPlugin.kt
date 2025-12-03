@file:Suppress("UnstableApiUsage")

package xyz.block.artifactswap

import com.fueledbycaffeine.spotlight.SpotlightSettingsPlugin
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList
import com.fueledbycaffeine.spotlight.buildscript.SpotlightRulesList
import com.fueledbycaffeine.spotlight.buildscript.graph.BreadthFirstSearch
import com.fueledbycaffeine.spotlight.buildscript.graph.StrictModeTypeSafeProjectAccessorRule
import com.fueledbycaffeine.spotlight.buildscript.models.SpotlightRules
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.resolve.DependencyResolutionManagement
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import xyz.block.artifactswap.core.module_selector.ArtifactSwapModuleSelectorFactory
import xyz.block.artifactswap.core.module_selector.ModuleSelectionResult
import xyz.block.gradle.LOCAL_PROTOS_ARTIFACTS
import xyz.block.gradle.hasPublishableComponent
import xyz.block.gradle.isArtifactPublishingEnabled
import xyz.block.gradle.services.services
import xyz.block.gradle.useArtifactSync
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
 * plugins { id 'xyz.block.artifactswap.settings' }
 */
@Suppress("unused")
class ArtifactSwapSettingsPlugin : Plugin<Settings> {

  override fun apply(target: Settings) =
    target.run {
      when {
        isIdeSync && useArtifactSync -> applyArtifactSwap()
        else -> applySpotlight()
      }
      maybeApplyPublishPlugin()
      maybeUseLocalProtos()
    }

  private fun Settings.applyArtifactSwap() {
    logger.lifecycle("Using Artifact Sync! See https://go/artifact-sync for docs.")
    val selectionResult = selectProjectsForArtifactSwap()
    selectionResult.selectedProjects.forEach { include(it.toString()) }
    logSelectionMetrics(selectionResult)
    setupArtifactSwapInfrastructure(selectionResult.bomVersion)
  }

  private fun Settings.applySpotlight() {
    logger.debug("Artifact Sync is inactive. Delegating to Spotlight.")
    pluginManager.apply(SpotlightSettingsPlugin::class.java)
  }

  private val Settings.allProjects: SpotlightProjectList
    get() = SpotlightProjectList.allProjects(settingsDir.toPath())

  private val Settings.ideProjects: SpotlightProjectList
    get() = SpotlightProjectList.ideProjects(settingsDir.toPath())

  private val Settings.spotlightRules: SpotlightRules
    get() = SpotlightRulesList(settingsDir.toPath()).read()

  /**
   * Select projects for artifact swap based on:
   * 1. User's requested projects (from setupIdeModules)
   * 2. Projects with local changes (from git)
   * 3. Projects without downloaded artifacts
   * 4. Transitive dependencies (from spotlight graph)
   */
  private fun Settings.selectProjectsForArtifactSwap(): ModuleSelectionResult {
    val ideProjectsList = ideProjects.read()
    val requestedProjects =
      ideProjectsList.ifEmpty {
        logSlowSyncWarning()
        allProjects.read()
      }

    // Making a choice here that artifact swap only supports strict type-safe accessor mode
    val typeSafeAccessorRule = StrictModeTypeSafeProjectAccessorRule(rootProject.name)

    // Use spotlight to compute the graph of possible projects that could need to be included
    // for the requested projects being loaded
    val possibleProjects =
      BreadthFirstSearch.flatten(
        requestedProjects,
        spotlightRules.implicitRules + typeSafeAccessorRule,
      )

    // Selector finds BOM version internally and decides which projects to include
    val selector =
      ArtifactSwapModuleSelectorFactory.create(settingsDir.toPath(), artifactSwapConfig)
    return selector.selectProjects(possibleProjects, requestedProjects)
  }

  private fun logSelectionMetrics(result: ModuleSelectionResult) {
    logger.lifecycle(
      "Artifact Swap module selection: {} selected out of {} candidates " +
        "(explicit: {}, always-keep: {}, local changes: {}, missing artifact: {}, excluded: {})",
      result.metrics.totalSelected,
      result.metrics.totalCandidates,
      result.metrics.selectedDueToExplicitRequest,
      result.metrics.selectedDueToAlwaysKeep,
      result.metrics.selectedDueToLocalChanges,
      result.metrics.selectedDueToMissingArtifact,
      result.metrics.excludedDueToArtifactAvailable,
    )
  }

  private fun Settings.logSlowSyncWarning() {
    if (isIdeSync) {
      logger.error(
        "Syncing the entire repo is very slow! " +
          "Use `./setupIdeModules` to select only the parts of the repo you need to sync."
      )
    }
  }

  /**
   * Set up the artifact swap infrastructure:
   * 1. Register BOM version service
   * 2. Configure maven local repository for artifacts
   * 3. Apply artifact swap plugins to all projects
   */
  private fun Settings.setupArtifactSwapInfrastructure(bomVersion: String) {
    logger.debug("Artifact Swap BOM version: {}", bomVersion)

    gradle.settingsEvaluated {
      // This has to run after settings are evaluated. It ensures that the project descriptor
      // registry contains the full list of projects that are `include`-ed by the applied settings
      // script.

      // Service should only be registered and retrieved when using artifact sync and during an
      // IDE sync
      gradle.services.register(ArtifactSwapBomService.KEY, ArtifactSwapBomService::class.java) {
        it.parameters.bomVersion.set(bomVersion)
      }
    }

    // Force Gradle to only look for swapped artifacts in maven local
    dependencyResolutionManagement.setupArtifactRepository()

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
  private fun DependencyResolutionManagement.setupArtifactRepository() {
    repositories.let { repos ->
      repos.exclusiveContent { ex ->
        ex.forRepositories(repos.mavenLocal())
        ex.filter { config -> config.includeGroup(ARTIFACT_SWAP_MAVEN_GROUP) }
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
        ex.filter { config -> config.includeGroup("com.squareup.protos") }
      }
    }
  }

  private companion object {
    val logger: Logger = Logging.getLogger(ArtifactSwapSettingsPlugin::class.java)
  }
}
