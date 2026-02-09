package xyz.block.artifactswap.model

import java.io.Serializable

/**
 * Model provided by the Artifact Swap Gradle plugin via Gradle Tooling API. This provides IDE
 * plugins with configuration information from the build.
 */
interface ArtifactSwapModel : Serializable {
  /** Maven group ID for swapped artifacts (e.g., "xyz.block.example.artifacts") */
  val mavenGroup: String

  /**
   * BOM version for swapped artifacts (e.g.,
   * "52E6B87B62D51260154DC6460FB5CAAFB8E050DCA74A2DD870D7E4B93FF126E3")
   */
  val bomVersion: String

  /**
   * Resolved path to the local Maven repository directory.
   *
   * Example: "/Users/user/.m2/repository"
   */
  val mavenLocalDirectory: String
}
