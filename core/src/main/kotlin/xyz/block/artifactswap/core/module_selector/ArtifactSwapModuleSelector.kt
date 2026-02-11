package xyz.block.artifactswap.core.module_selector

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.graph.BreadthFirstSearch
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.TimedValue
import kotlin.time.measureTimedValue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import xyz.block.artifactswap.core.download.services.ArtifactSyncBomLoader
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.repository.InstalledArtifact
import xyz.block.artifactswap.core.repository.LocalArtifactRepository
import xyz.block.artifactswap.core.shared_services.git.SquareGit

/** Reason a module was included or excluded during artifact swap selection. */
enum class InclusionReason {
  EXPLICITLY_REQUESTED,
  ALWAYS_KEEP,
  LOCAL_CHANGES,
  MISSING_ARTIFACT,
  EXCLUDED,
}

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

data class ModuleSelectionResult(
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
  /**
   * Selects which projects should be included in the build.
   *
   * @param requestedProjects Projects explicitly requested by the user
   * @return Selection result with included projects and metrics
   */
  fun selectProjects(requestedProjects: Set<GradlePath>): ModuleSelectionResult
}

class RealArtifactSwapModuleSelector(
  private val localArtifactRepository: LocalArtifactRepository,
  private val squareGit: SquareGit,
  private val bomLoader: ArtifactSyncBomLoader,
  private val ioDispatcher: CoroutineContext,
  private val eventstream: Eventstream,
  private val spotlightRules: Set<DependencyRule>,
  private val alwaysKeepProjects: Set<GradlePath> = emptySet(),
) : ArtifactSwapModuleSelector {
  private companion object {
    val LOGGER: Logger = LoggerFactory.getLogger(RealArtifactSwapModuleSelector::class.java)
  }

  override fun selectProjects(requestedProjects: Set<GradlePath>): ModuleSelectionResult {
    var selectionEvent = ModuleSelectionEvent()
    return try {
      val (result, totalDuration) =
        measureTimedValue { runBlocking { computeSelection(requestedProjects) } }

      selectionEvent = ModuleSelectionEvent(result, totalDuration)
      result
    } catch (e: Exception) {
      LOGGER.error("Project selection failed", e)
      val failureResult =
        when (e) {
          is ModuleSelectorException -> e.result
          else -> ModuleSelectionEventResult.UNKNOWN_FAILURE
        }

      selectionEvent = ModuleSelectionEvent(failureResult)
      throw e
    } finally {
      eventstream.sendEvents(listOf(selectionEvent.toEventStreamEvent()))
    }
  }

  /**
   * This method wraps all of the various IO operations that need to happen in parallel and computes
   * the actual selection result.
   */
  private suspend fun computeSelection(requestedProjects: Set<GradlePath>): ModuleSelectionResult =
    coroutineScope {
      // First step is to locate a compatible artifact BOM
      val bomJob = async(ioDispatcher) { determineBomVersion() }
      // Determine the range of projects that could possibly need to be included for the user's
      // requested projects.
      // This can run in parallel with finding the BOM
      val candidatesJob =
        async(ioDispatcher) { BreadthFirstSearch.flatten(requestedProjects, spotlightRules) }

      val (bomVersion, bomDuration) = bomJob.await()
      val candidates = candidatesJob.await()

      // Next, determine what local git changes have invalidated artifacts provided by the BOM
      val gitJob = async(ioDispatcher) { findLocalGitChanges(bomVersion, candidates) }
      // And at the same time, determine what artifacts from the BOM are actually available locally
      val artifactsJob = async(ioDispatcher) { loadLocalArtifacts(bomVersion) }

      val (gitResult, gitDuration) = gitJob.await()
      val (installedArtifacts, artifactsDuration) = artifactsJob.await()

      val (changedProjects, changedFilesCount) = gitResult
      val (selectionResult, selectionDuration) =
        selectProjectsAndComputeMetrics(
          candidates,
          requestedProjects,
          changedProjects,
          installedArtifacts,
        )
      val (selectedProjects, metrics) = selectionResult

      ModuleSelectionResult(
        selectedProjects = selectedProjects,
        metrics = metrics,
        bomVersionToUse = bomVersion,
        bomDuration = bomDuration,
        gitDuration = gitDuration,
        locallyChangedFilesCount = changedFilesCount,
        artifactsDuration = artifactsDuration,
        swappableArtifactsCount = installedArtifacts.size,
        selectionDuration = selectionDuration,
      )
    }

  private suspend fun determineBomVersion(): TimedValue<String> {
    return measureTimedValue {
      try {
        bomLoader.findBestBomVersion().getOrThrow()
      } catch (e: Exception) {
        throw ModuleSelectorException.FailedDeterminingBomVersionException(e)
      }
    }
  }

  private suspend fun findLocalGitChanges(
    bomVersion: String,
    candidates: Set<GradlePath>,
  ): TimedValue<Pair<Set<GradlePath>, Int>> {
    return measureTimedValue {
      try {
        val files = squareGit.findChangedFiles(baseRef = bomVersion).getOrThrow()

        // Convert changed files to the projects that contain them
        val projectsWithChanges =
          candidates
            .filter { project -> files.any { file -> file.startsWith(project.projectDir) } }
            .toSet()

        projectsWithChanges to files.size
      } catch (e: Exception) {
        throw ModuleSelectorException.FailedDeterminingLocalGitChangesException(e)
      }
    }
  }

  private suspend fun loadLocalArtifacts(bomVersion: String): TimedValue<Set<InstalledArtifact>> {
    return measureTimedValue {
      try {
        bomLoader
          .loadBom(bomVersion)
          .mapCatching { bom ->
            localArtifactRepository.getInstalledArtifacts(bom = bom).getOrThrow()
          }
          .getOrThrow()
      } catch (e: Exception) {
        throw ModuleSelectorException.FailedReadingLocalArtifactStateException(e)
      }
    }
  }

  private fun selectProjectsAndComputeMetrics(
    candidates: Set<GradlePath>,
    requestedProjects: Set<GradlePath>,
    projectsWithChanges: Set<GradlePath>,
    swappableArtifacts: Set<InstalledArtifact>,
  ): TimedValue<Pair<Set<GradlePath>, SelectionMetrics>> = measureTimedValue {
    val root = candidates.first().root
    val locallyInstalledArtifacts =
      swappableArtifacts.map { GradlePath(root, it.projectPath) }.toSet()

    val projectDecisions =
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

    // Log each project's inclusion decision at info level
    for ((project, reason) in projectDecisions) {
      LOGGER.info("Artifact Swap decision: {} -> {}", project.path, reason)
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

    selectedProjects to metrics
  }

  sealed class ModuleSelectorException(
    val result: ModuleSelectionEventResult,
    message: String,
    cause: Exception,
  ) : Exception(message, cause) {
    class FailedDeterminingBomVersionException(cause: Exception) :
      ModuleSelectorException(
        ModuleSelectionEventResult.FAILED_DETERMINING_BOM_VERSION,
        "Failed to determine BOM version",
        cause,
      )

    class FailedDeterminingLocalGitChangesException(cause: Exception) :
      ModuleSelectorException(
        ModuleSelectionEventResult.FAILED_READING_LOCAL_GIT_CHANGES,
        "Failed to determine local git changes",
        cause,
      )

    class FailedReadingLocalArtifactStateException(cause: Exception) :
      ModuleSelectorException(
        ModuleSelectionEventResult.FAILED_READING_LOCAL_ARTIFACTS,
        "Failed to read local artifact state",
        cause,
      )
  }
}
