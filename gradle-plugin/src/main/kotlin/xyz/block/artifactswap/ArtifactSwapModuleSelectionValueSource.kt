@file:Suppress("UnstableApiUsage")

package xyz.block.artifactswap

import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList
import com.fueledbycaffeine.spotlight.buildscript.SpotlightRulesList
import com.fueledbycaffeine.spotlight.buildscript.graph.StrictModeTypeSafeProjectAccessorRule
import java.io.Serializable
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import xyz.block.artifactswap.core.config.ArtifactSwapConfig

/**
 * ValueSource that performs module selection for artifact swap.
 *
 * This encapsulates all the I/O operations (git, file system, network) inside a ValueSource so that
 * only the output (selected projects and BOM version) affects the configuration cache, not the
 * intermediate operations.
 */
internal abstract class ArtifactSwapModuleSelectionValueSource :
  ValueSource<
    ArtifactSwapModuleSelectionValueSource.Result,
    ArtifactSwapModuleSelectionValueSource.Parameters,
  > {

  interface Parameters : ValueSourceParameters {
    val rootDirectory: DirectoryProperty
    val rootProjectName: Property<String>
    val config: Property<ArtifactSwapConfig>
  }

  data class Result(
    val selectedProjects: Set<String>, // Project paths as strings for serialization
    val bomVersion: String,
    val metrics: SelectionMetrics,
  ) : Serializable {
    data class SelectionMetrics(
      val totalCandidates: Int,
      val totalSelected: Int,
      val selectedDueToExplicitRequest: Int,
      val selectedDueToAlwaysKeep: Int,
      val selectedDueToLocalChanges: Int,
      val selectedDueToMissingArtifact: Int,
      val excludedDueToArtifactAvailable: Int,
    ) : Serializable
  }

  override fun obtain(): Result {
    val rootDir = parameters.rootDirectory.get().asFile.toPath()
    val rootProjectName = parameters.rootProjectName.get()
    val config = parameters.config.get()

    // Read project lists
    val ideProjectsList = SpotlightProjectList.ideProjects(rootDir).read()
    val requestedProjects =
      ideProjectsList.ifEmpty {
        // If empty, load all projects
        val allProjects = SpotlightProjectList.allProjects(rootDir).read()
        check(allProjects.isNotEmpty()) {
          "${SpotlightProjectList.ALL_PROJECTS_LOCATION} is missing/empty!"
        }
        logger.error(
          "Syncing the entire repo is very slow! " +
            "Use the Spotlight IDE plugin to select only the parts of the repo you need to sync."
        )
        allProjects
      }

    // Read spotlight rules
    val spotlightRules = SpotlightRulesList(rootDir).read()
    val typeSafeAccessorRule = StrictModeTypeSafeProjectAccessorRule(rootProjectName)
    val allRules = spotlightRules.implicitRules + typeSafeAccessorRule

    val selector = ArtifactSwapModuleSelectorFactory.create(rootDir, config, allRules)
    val selectionResult = selector.selectProjects(requestedProjects)

    val result =
      Result(
        selectedProjects = selectionResult.selectedProjects.map { it.path }.toSet(),
        bomVersion = selectionResult.bomVersionToUse,
        metrics =
          Result.SelectionMetrics(
            totalCandidates = selectionResult.metrics.totalCandidates,
            totalSelected = selectionResult.metrics.totalSelected,
            selectedDueToExplicitRequest = selectionResult.metrics.selectedDueToExplicitRequest,
            selectedDueToAlwaysKeep = selectionResult.metrics.selectedDueToAlwaysKeep,
            selectedDueToLocalChanges = selectionResult.metrics.selectedDueToLocalChanges,
            selectedDueToMissingArtifact = selectionResult.metrics.selectedDueToMissingArtifact,
            excludedDueToArtifactAvailable = selectionResult.metrics.excludedDueToArtifactAvailable,
          ),
      )
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

    return result
  }

  private companion object {
    val logger = Logging.getLogger(ArtifactSwapModuleSelectionValueSource::class.java)
  }
}
