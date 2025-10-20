package xyz.block.artifactswap.gradle

import org.gradle.api.provider.Property
import java.io.File

/**
 * Configuration extension for the Artifact Swap plugin.
 *
 * Users can configure this in their settings.gradle.kts:
 * ```
 * artifactSwap {
 *   mavenGroup.set("com.example.artifacts")
 *   bomVersion.set("1.0.0")
 *   localRepositoryPath.set(file("/custom/path/to/repo"))
 * }
 * ```
 */
interface ArtifactSwapExtension {
  /**
   * The Maven group ID used for artifact swap dependencies.
   *
   * Example: "com.example.artifacts"
   */
  val mavenGroup: Property<String>

  /**
   * The version of the BOM (Bill of Materials) to use for dependency resolution.
   *
   * Example: "1.0.0"
   */
  val bomVersion: Property<String>

  /**
   * The path to the local Maven repository where artifacts are stored.
   *
   * Defaults to ~/.m2/repository
   */
  val localRepositoryPath: Property<File>
}
