package xyz.block.artifactswap.idea.gradle

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.util.concurrent.ConcurrentHashMap
import xyz.block.artifactswap.idea.util.ArtifactSwapMavenLocalHelper
import xyz.block.artifactswap.model.ArtifactSwapModel

/**
 * Service that pre-scans AAR files in the background after Gradle sync to populate the package
 * cache. This makes navigation to Android resources instant.
 *
 * The scan runs on a background thread and is cancellable.
 */
@Service(Service.Level.PROJECT)
class AarPackageCacheService(private val project: Project) {

  private val logger = Logger.getInstance(AarPackageCacheService::class.java)

  // Cache mapping of Android namespace (package) to artifact ID
  // This is the actual lookup cache used for navigation
  private val packageToArtifactCache = ConcurrentHashMap<String, String>()

  /** Clears the cache. Called when Gradle sync completes. */
  fun clearCache() {
    packageToArtifactCache.clear()
  }

  /** Gets the cached artifact ID for a package name, or null if not cached. */
  fun getArtifactForPackage(packageName: String): String? {
    return packageToArtifactCache[packageName]
  }

  /**
   * Caches the package → artifact mapping. Should be called after successfully extracting package
   * name from an AAR.
   */
  fun cachePackageMapping(packageName: String, artifactId: String) {
    packageToArtifactCache[packageName] = artifactId
  }

  /**
   * Scans all AARs from the BOM in the background and populates the package cache. Called after
   * Gradle sync completes.
   */
  fun scanAarsInBackground(model: ArtifactSwapModel) {
    // Run scan as a background task with progress indicator
    ProgressManager.getInstance()
      .run(
        object : Task.Backgroundable(project, "Scanning Android AARs", true) {
          override fun run(indicator: ProgressIndicator) {
            scanAars(indicator, model.mavenGroup, model.bomVersion)
          }
        }
      )
  }

  private fun scanAars(indicator: ProgressIndicator, mavenGroup: String, bomVersion: String) {
    val startTime = System.currentTimeMillis()

    indicator.text = "Parsing BOM for artifact versions"

    // Parse the BOM to get all artifact versions
    val artifactVersions =
      ArtifactSwapMavenLocalHelper.parseBomVersions(project, mavenGroup, bomVersion)
    if (artifactVersions == null) {
      logger.warn("Could not parse BOM versions for $mavenGroup:bom:$bomVersion")
      return
    }

    val userHome = System.getProperty("user.home")
    val groupPath = mavenGroup.replace('.', '/')
    val mavenLocalGroupPath = "$userHome/.m2/repository/$groupPath"
    val groupDir = LocalFileSystem.getInstance().findFileByPath(mavenLocalGroupPath)
    if (groupDir == null) {
      logger.debug("Maven local group directory not found: $mavenLocalGroupPath")
      return
    }

    val totalArtifacts = artifactVersions.size
    indicator.text = "Scanning $totalArtifacts AAR files"

    artifactVersions.entries.forEachIndexed { index, (artifactId, version) ->
      if (indicator.isCanceled) {
        val duration = System.currentTimeMillis() - startTime
        val scannedCount = index + 1
        logger.info(
          "AAR scan cancelled after $scannedCount/$totalArtifacts artifacts (${duration}ms)"
        )
        return
      }

      val currentCount = index + 1
      indicator.fraction = currentCount.toDouble() / totalArtifacts
      indicator.text2 = "Scanning $artifactId ($currentCount/$totalArtifacts)"

      // Look for the specific AAR file
      val aarFileName = "$artifactId-$version.aar"
      val aarFile = groupDir.findChild(artifactId)?.findChild(version)?.findChild(aarFileName)

      if (aarFile != null && aarFile.exists()) {
        val manifestPackage = ArtifactSwapMavenLocalHelper.extractPackageFromAar(project, aarFile)
        if (manifestPackage != null) {
          cachePackageMapping(manifestPackage, artifactId)
          logger.debug("Cached package for $artifactId: $manifestPackage")
        }
      }
    }

    val duration = System.currentTimeMillis() - startTime
    logger.info(
      "AAR scan complete: cached ${packageToArtifactCache.size} packages from $totalArtifacts artifacts in ${duration}ms"
    )
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): AarPackageCacheService =
      project.getService(AarPackageCacheService::class.java)
  }
}
