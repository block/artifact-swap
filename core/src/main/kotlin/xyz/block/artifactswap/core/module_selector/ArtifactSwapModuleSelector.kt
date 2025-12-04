package xyz.block.artifactswap.core.module_selector

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import kotlin.collections.map
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.measureTimedValue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import xyz.block.artifactswap.core.download.services.ArtifactSyncBomLoader
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.repository.InstalledArtifact
import xyz.block.artifactswap.core.repository.LocalArtifactRepository

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
    val (result, totalDuration) =
      measureTimedValue { runBlocking { computeSelection(candidates, requestedProjects) } }

    val event = ModuleSelectionEvent(result, totalDuration)
    eventstream.sendEvents(listOf(event.toEventStreamEvent()))

    return ModuleSelectionResult(
      selectedProjects = result.selectedProjects,
      metrics = result.metrics,
      bomVersion = result.bomVersionToUse,
      event = event,
    )
  }

  private suspend fun computeSelection(
    candidates: Set<GradlePath>,
    requestedProjects: Set<GradlePath>,
  ): SelectionComputationResult {
    val (bomVersion, bomDuration) = determineBomVersion()
    val (projectsWithChanges, changedFilesCount, gitDuration) =
      findLocalGitChanges(bomVersion, bomDuration, candidates)
    val (swappableArtifacts, artifactsDuration) =
      loadLocalArtifacts(bomVersion, bomDuration, gitDuration, changedFilesCount)

    val (selectedProjects, metrics, selectionDuration) =
      selectProjectsAndComputeMetrics(
        candidates,
        requestedProjects,
        projectsWithChanges,
        swappableArtifacts,
      )

    return SelectionComputationResult(
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
  }

  private suspend fun determineBomVersion(): Pair<String, Duration> {
    val timedValue = measureTimedValue {
      try {
        coroutineScope {
          val bom = async(ioDispatcher) { bomLoader.findBestBomVersion().getOrThrow() }
          bom.await()
        }
      } catch (e: Exception) {
        LOGGER.error("Failed to determine BOM version", e)
        val failureEvent =
          ModuleSelectionEvent(
            result = ModuleSelectionEventResult.FAILED_DETERMINING_BOM_VERSION,
            determineBomVersionDurationMs = 0,
            bomVersionUsed = "",
            gitDetermineLocalChangesDurationMs = 0,
            totalDurationMs = 0,
          )
        eventstream.sendEvents(listOf(failureEvent.toEventStreamEvent()))
        throw e
      }
    }
    return timedValue.value to timedValue.duration
  }

  private suspend fun findLocalGitChanges(
    bomVersion: String,
    bomDuration: Duration,
    candidates: Set<GradlePath>,
  ): Triple<Set<GradlePath>, Int, Duration> {
    val (result, duration) =
      measureTimedValue {
        try {
          coroutineScope {
            val changedFiles =
              async(ioDispatcher) {
                squareGit.findChangedFiles(baseCommit = bomVersion).getOrThrow()
              }
            val files = changedFiles.await()

            // Convert changed files to the projects that contain them
            val projectsWithChanges =
              candidates
                .filter { project -> files.any { file -> file.startsWith(project.projectDir) } }
                .toSet()

            projectsWithChanges to files.size
          }
        } catch (e: Exception) {
          LOGGER.error("Failed to determine local git changes", e)
          val failureEvent =
            ModuleSelectionEvent(
              result = ModuleSelectionEventResult.FAILED_READING_LOCAL_GIT_CHANGES,
              determineBomVersionDurationMs = bomDuration.inWholeMilliseconds,
              bomVersionUsed = bomVersion,
              gitDetermineLocalChangesDurationMs = 0,
              totalDurationMs = 0,
            )
          eventstream.sendEvents(listOf(failureEvent.toEventStreamEvent()))
          throw RuntimeException("Failed to read local git changes", e)
        }
      }
    val (projectsWithChanges, fileCount) = result
    return Triple(projectsWithChanges, fileCount, duration)
  }

  private suspend fun loadLocalArtifacts(
    bomVersion: String,
    bomDuration: Duration,
    gitDuration: Duration,
    changedFilesCount: Int,
  ): Pair<Set<InstalledArtifact>, Duration> {
    val timedValue = measureTimedValue {
      try {
        coroutineScope {
          val artifacts =
            async(ioDispatcher) {
              bomLoader
                .loadBom(bomVersion)
                .mapCatching { bom ->
                  localArtifactRepository.getInstalledArtifacts(bom = bom).getOrThrow()
                }
                .getOrThrow()
            }
          artifacts.await()
        }
      } catch (e: Exception) {
        LOGGER.error("Failed to determine local artifacts", e)
        val failureEvent =
          ModuleSelectionEvent(
            result = ModuleSelectionEventResult.FAILED_READING_LOCAL_ARTIFACTS,
            determineBomVersionDurationMs = bomDuration.inWholeMilliseconds,
            bomVersionUsed = bomVersion,
            gitDetermineLocalChangesDurationMs = gitDuration.inWholeMilliseconds,
            locallyChangedFilesCount = changedFilesCount,
            determineLocalArtifactsDurationMs = 0,
            totalDurationMs = 0,
          )
        eventstream.sendEvents(listOf(failureEvent.toEventStreamEvent()))
        throw RuntimeException("Failed to read local artifacts", e)
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
}
