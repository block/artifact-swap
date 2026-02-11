package xyz.block.artifactswap.functionaltest.fixtures

import org.gradle.testkit.runner.BuildResult
import xyz.block.artifactswap.core.module_selector.InclusionReason

/**
 * Parsed artifact swap module selection results from a Gradle build output.
 *
 * Extracts both the summary counts and per-module decisions from the build log, enabling clean
 * assertions in functional tests via standard Truth matchers:
 * ```
 * val selection = result.artifactSwapSelection()
 * assertThat(selection.totalCandidates).isEqualTo(2)
 * assertThat(selection.decisionFor(":lib")).isEqualTo(InclusionReason.EXCLUDED)
 * ```
 */
data class ArtifactSwapSelectionResult(
  val totalSelected: Int,
  val totalCandidates: Int,
  val explicitCount: Int,
  val alwaysKeepCount: Int,
  val localChangesCount: Int,
  val missingArtifactCount: Int,
  val excludedCount: Int,
  /** Map of module path to its inclusion/exclusion decision. */
  val moduleDecisions: Map<String, InclusionReason>,
) {

  /** Returns the decision for a specific module, failing if the module wasn't found. */
  fun decisionFor(modulePath: String): InclusionReason =
    moduleDecisions[modulePath]
      ?: throw AssertionError(
        "No decision found for module '$modulePath'. " +
          "Available modules: ${moduleDecisions.keys}"
      )

  companion object {
    private val SUMMARY_PATTERN =
      Regex(
        """(\d+) selected out of (\d+) candidates \(explicit: (\d+), always-keep: (\d+), local changes: (\d+), missing artifact: (\d+), excluded: (\d+)\)"""
      )

    private val DECISION_PATTERN = Regex("""Artifact Swap decision: (:\S+) -> (\S+)""")

    fun parse(output: String): ArtifactSwapSelectionResult {
      val summaryMatch =
        SUMMARY_PATTERN.find(output)
          ?: throw AssertionError(
            "Artifact Swap module selection summary not found in build output. " +
              "Make sure --info is passed and artifact swap is active."
          )

      val decisions =
        DECISION_PATTERN.findAll(output).associate { match ->
          match.groupValues[1] to InclusionReason.valueOf(match.groupValues[2])
        }

      return ArtifactSwapSelectionResult(
        totalSelected = summaryMatch.groupValues[1].toInt(),
        totalCandidates = summaryMatch.groupValues[2].toInt(),
        explicitCount = summaryMatch.groupValues[3].toInt(),
        alwaysKeepCount = summaryMatch.groupValues[4].toInt(),
        localChangesCount = summaryMatch.groupValues[5].toInt(),
        missingArtifactCount = summaryMatch.groupValues[6].toInt(),
        excludedCount = summaryMatch.groupValues[7].toInt(),
        moduleDecisions = decisions,
      )
    }
  }
}

/** Parses artifact swap selection results from the build output. */
fun BuildResult.artifactSwapSelection(): ArtifactSwapSelectionResult =
  ArtifactSwapSelectionResult.parse(output)
