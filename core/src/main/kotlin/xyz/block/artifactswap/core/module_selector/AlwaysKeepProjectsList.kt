package xyz.block.artifactswap.core.module_selector

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

/**
 * Manages the list of projects that must always be included in the build, even if they have
 * available artifacts.
 *
 * This is useful for cases where:
 * - A project is a SQLDelight schema dependency (SQLDelight cannot use binary artifacts as schema
 *   dependencies)
 * - A project has special build-time requirements that prevent artifact substitution
 *
 * The list is stored in `gradle/artifact-swap-always-keep.txt` with one project path per line. The
 * file format is the same as Spotlight project list files:
 * - One project path per line (e.g., `:common:schema`)
 * - Lines starting with `#` are comments
 * - Empty lines are ignored
 */
object AlwaysKeepProjectsList {
  private const val ALWAYS_KEEP_FILE = "gradle/artifact-swap-always-keep.txt"

  /**
   * Reads the list of projects that should always be kept from the configuration file.
   *
   * Uses the same file format as SpotlightProjectList for consistency.
   *
   * @param rootDir The root directory of the Gradle project
   * @return Set of GradlePath objects that must always be included (e.g., ":common:schema")
   */
  fun read(rootDir: Path): Set<GradlePath> {
    val file = rootDir.resolve(ALWAYS_KEEP_FILE)
    if (!file.exists() || !file.isRegularFile()) {
      return emptySet()
    }

    return Files.readAllLines(file)
      .asSequence()
      .map { it.trim() }
      .filter { it.isNotEmpty() && !it.startsWith("#") } // Support comments like Spotlight
      .map { GradlePath(rootDir, it) } // Convert string paths to GradlePath objects
      .toSet()
  }
}
