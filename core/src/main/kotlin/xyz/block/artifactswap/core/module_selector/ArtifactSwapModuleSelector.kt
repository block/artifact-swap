package xyz.block.artifactswap.core.module_selector

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import kotlin.collections.map
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.TimedValue
import kotlin.time.measureTimedValue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import xyz.block.artifactswap.core.download.services.ArtifactSyncBomLoader
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.repository.InstalledArtifact
import xyz.block.artifactswap.core.repository.LocalArtifactRepository
import xyz.block.artifactswap.core.shared_services.git.SquareGit

private val LOGGER = LoggerFactory.getLogger(RealArtifactSwapModuleSelector::class.java)

/** Result of project selection with metrics about why projects were included. */
data class ModuleSelectionResult(
  val selectedProjects: Set<GradlePath>,
  val metrics: SelectionMetrics,
  val bomVersion: String,
  val event: ModuleSelectionEvent,
)

/** Metrics about project selection decisions. */
data class SelectionMetrics(
  val totalCandidates: Int,
  val totalSelected: Int,
  val selectedDueToExplicitRequest: Int,
  val selectedDueToAlwaysKeep: Int,
  val selectedDueToLocalChanges: Int,
  val selectedDueToMissingArtifact: Int,
  val excludedDueToArtifactAvailable: Int,
)

/** Internal result data for module selection computation. */
internal data class SelectionComputationResult(
  val selectedProjects: Set<GradlePath>,
  val metrics: SelectionMetrics,
  val bomVersionToUse: String,
  val bomDuration: Duration,
  val gitDuration: Duration,
  val locallyChangedFilesCount: Int,
  val artifactsDuration: Duration,
  val swappableArtifactsCount: Int,
  val selectionDuration: Duration,
)

interface ArtifactSwapModuleSelector {
  fun selectProjects(
    candidates: Set<GradlePath>,
    requestedProjects: Set<GradlePath>,
  ): ModuleSelectionResult
}

class RealArtifactSwapModuleSelector(
  private val localArtifactRepository: LocalArtifactRepository,
  private val squareGit: SquareGit,
  private val bomLoader: ArtifactSyncBomLoader,
  private val ioDispatcher: CoroutineContext,
  private val eventstream: Eventstream,
  private val alwaysKeepProjects: Set<GradlePath> = emptySet(),
) : ArtifactSwapModuleSelector {

  override fun selectProjects(
    candidates: Set<GradlePath>,
    requestedProjects: Set<GradlePath>,
  ): ModuleSelectionResult {
    var selectionEvent = ModuleSelectionEvent()
    try {
      val (result, totalDuration) =
        measureTimedValue { runBlocking { computeSelection(candidates, requestedProjects) } }

      selectionEvent = ModuleSelectionEvent(result, totalDuration)
      return ModuleSelectionResult(
        selectedProjects = result.selectedProjects,
        metrics = result.metrics,
        bomVersion = result.bomVersionToUse,
        event = selectionEvent,
      )
    } catch (e: Exception) {
      val failureResult =
        if (e is ModuleSelectorException) {
          when (e) {
            is ModuleSelectorException.FailedDeterminingBomVersionException ->
              ModuleSelectionEventResult.FAILED_DETERMINING_BOM_VERSION
            is ModuleSelectorException.FailedDeterminingLocalGitChangesException ->
              ModuleSelectionEventResult.FAILED_READING_LOCAL_GIT_CHANGES
            is ModuleSelectorException.FailedReadingLocalArtifactStateException ->
              ModuleSelectionEventResult.FAILED_READING_LOCAL_ARTIFACTS
          }
        } else {
          ModuleSelectionEventResult.UNKNOWN_FAILURE
        }
      selectionEvent = selectionEvent.copy(result = failureResult)
      throw e
    } finally {
      eventstream.sendEvents(listOf(selectionEvent.toEventStreamEvent()))
    }
  }

  private suspend fun computeSelection(
    candidates: Set<GradlePath>,
    requestedProjects: Set<GradlePath>,
  ): SelectionComputationResult {
    val (bomVersion, bomDuration) = determineBomVersion()
    // run these two operations concurrently, performance often important when running this
    // method as it runs before/blocks usage of IDE until it completes
    val deferredLocalGitChanges = coroutineScope {
      async(ioDispatcher) { findLocalGitChanges(bomVersion, candidates) }
    }
    val deferredLocalArtifacts = coroutineScope {
      async(ioDispatcher) { loadLocalArtifacts(bomVersion) }
    }

    val (projectsWithChanges, changedFilesCount, gitDuration) = deferredLocalGitChanges.await()
    val (swappableArtifacts, artifactsDuration) = deferredLocalArtifacts.await()

    val (selectedProjects, metrics, selectionDuration) =
      selectProjectsAndComputeMetrics(
        candidates,
        requestedProjects,
        projectsWithChanges,
        swappableArtifacts,
      )
    val result =
      SelectionComputationResult(
        selectedProjects = selectedProjects,
        metrics = metrics,
        bomVersionToUse = bomVersion,
        bomDuration = bomDuration,
        gitDuration = gitDuration,
        locallyChangedFilesCount = changedFilesCount,
        artifactsDuration = artifactsDuration,
        swappableArtifactsCount = swappableArtifacts.size,
        selectionDuration = selectionDuration,
      )
    return result
  }

  private suspend fun determineBomVersion(): TimedValue<String> = measureTimedValue {
    try {
      bomLoader.findBestBomVersion().getOrThrow()
    } catch (e: Exception) {
      LOGGER.error("Failed to determine BOM version", e)
      throw ModuleSelectorException.FailedDeterminingBomVersionException(e)
    }
  }

  private suspend fun findLocalGitChanges(
    bomVersion: String,
    candidates: Set<GradlePath>,
  ): Triple<Set<GradlePath>, Int, Duration> {
    val (result, duration) =
      measureTimedValue {
        try {
          val files = squareGit.findChangedFiles(baseRef = bomVersion).getOrThrow()
          // Convert changed files to the projects that contain them
          val projectsWithChanges =
            candidates
              .filter { project -> files.any { file -> file.startsWith(project.projectDir) } }
              .toSet()
          projectsWithChanges to files.size
        } catch (e: Exception) {
          LOGGER.error("Failed to determine local git changes", e)
          throw ModuleSelectorException.FailedDeterminingLocalGitChangesException(e)
        }
      }
    val (projectsWithChanges, fileCount) = result
    return Triple(projectsWithChanges, fileCount, duration)
  }

  private suspend fun loadLocalArtifacts(
    bomVersion: String
  ): Pair<Set<InstalledArtifact>, Duration> {
    val timedValue = measureTimedValue {
      try {
        bomLoader
          .loadBom(bomVersion)
          .mapCatching { bom ->
            localArtifactRepository.getInstalledArtifacts(bom = bom).getOrThrow()
          }
          .getOrThrow()
      } catch (e: Exception) {
        LOGGER.error("Failed to determine local artifacts", e)
        throw ModuleSelectorException.FailedReadingLocalArtifactStateException(e)
      }
    }
    return timedValue.value to timedValue.duration
  }

  private fun selectProjectsAndComputeMetrics(
    candidates: Set<GradlePath>,
    requestedProjects: Set<GradlePath>,
    projectsWithChanges: Set<GradlePath>,
    swappableArtifacts: Set<InstalledArtifact>,
  ): Triple<Set<GradlePath>, SelectionMetrics, Duration> {
    val root = candidates.first().root
    val locallyInstalledArtifacts =
      swappableArtifacts.map { GradlePath(root, it.projectPath) }.toSet()

    val (projectDecisions, selectionDuration) =
      measureTimedValue {
        candidates.map { project ->
          project to
            when (project) {
              in requestedProjects -> InclusionReason.EXPLICITLY_REQUESTED
              in alwaysKeepProjects -> InclusionReason.ALWAYS_KEEP
              in projectsWithChanges -> InclusionReason.LOCAL_CHANGES
              !in locallyInstalledArtifacts -> InclusionReason.MISSING_ARTIFACT
              else -> InclusionReason.EXCLUDED
            }
        }
      }

    val decisionCounts = projectDecisions.groupingBy { it.second }.eachCount()
    val selectedProjects =
      projectDecisions.filter { it.second != InclusionReason.EXCLUDED }.map { it.first }.toSet()

    val metrics =
      SelectionMetrics(
        totalCandidates = candidates.size,
        totalSelected = selectedProjects.size,
        selectedDueToExplicitRequest = decisionCounts[InclusionReason.EXPLICITLY_REQUESTED] ?: 0,
        selectedDueToAlwaysKeep = decisionCounts[InclusionReason.ALWAYS_KEEP] ?: 0,
        selectedDueToLocalChanges = decisionCounts[InclusionReason.LOCAL_CHANGES] ?: 0,
        selectedDueToMissingArtifact = decisionCounts[InclusionReason.MISSING_ARTIFACT] ?: 0,
        excludedDueToArtifactAvailable = decisionCounts[InclusionReason.EXCLUDED] ?: 0,
      )

    return Triple(selectedProjects, metrics, selectionDuration)
  }

  private enum class InclusionReason {
    EXPLICITLY_REQUESTED,
    ALWAYS_KEEP,
    LOCAL_CHANGES,
    MISSING_ARTIFACT,
    EXCLUDED,
  }

  sealed class ModuleSelectorException(message: String, cause: Exception) :
    Exception(message, cause) {
    class FailedDeterminingBomVersionException(cause: Exception) :
      ModuleSelectorException("Failed to determine BOM version", cause)

    class FailedDeterminingLocalGitChangesException(cause: Exception) :
      ModuleSelectorException("Failed to determine local git changes", cause)

    class FailedReadingLocalArtifactStateException(cause: Exception) :
      ModuleSelectorException("Failed to read local artifact state", cause)
  }
}
