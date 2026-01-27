package xyz.block.artifactswap.idea.util

import com.intellij.openapi.vfs.VirtualFile

/** Utility functions for working with Android/Gradle source sets. */
object SourceSetUtils {

  /**
   * Orders source set directories by priority, placing "main" first.
   *
   * This is useful when searching for resources or source files across multiple source sets, as the
   * "main" source set should always be checked first.
   *
   * @param sourceSets The source set directories to order
   * @return The same directories, sorted with "main" first, others in original order
   */
  fun orderByPriority(sourceSets: List<VirtualFile>): List<VirtualFile> {
    return sourceSets.sortedBy { if (it.name == "main") 0 else 1 }
  }

  /**
   * Gets all source set directories under a "src" directory, ordered by priority (main first).
   *
   * @param srcDir The "src" directory containing source sets
   * @return List of source set directories, with "main" first, filtered to directories only
   */
  fun getSourceSets(srcDir: VirtualFile): List<VirtualFile> {
    return orderByPriority(srcDir.children.filter { it.isDirectory })
  }
}
