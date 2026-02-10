package xyz.block.artifactswap.idea.util

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.xml.XmlFile
import java.util.concurrent.ConcurrentHashMap

/**
 * Helper for interacting with artifact-swap artifacts in Maven Local repository. Handles BOM
 * parsing and AAR manifest extraction.
 */
object ArtifactSwapMavenLocalHelper {

  private val logger = Logger.getInstance(ArtifactSwapMavenLocalHelper::class.java)

  // Cache BOM artifact versions to avoid re-parsing
  private val bomVersionCache = ConcurrentHashMap<String, Map<String, String>>()

  /**
   * Parses a BOM POM file to extract artifact versions from dependencyManagement section. Results
   * are cached by "{mavenGroup}:{bomVersion}" key.
   */
  fun parseBomVersions(
    project: Project,
    mavenGroup: String,
    bomVersion: String,
    mavenLocalDirectory: String,
  ): Map<String, String>? {
    val cacheKey = "$mavenGroup:$bomVersion"

    // Check cache first
    bomVersionCache[cacheKey]?.let {
      return it
    }

    val groupPath = mavenGroup.replace('.', '/')
    val bomPomPath = "$mavenLocalDirectory/$groupPath/bom/$bomVersion/bom-$bomVersion.pom"

    val bomPomFile = LocalFileSystem.getInstance().findFileByPath(bomPomPath) ?: return null

    // Read file contents and parse as XML (POM files might not be detected as XML by PSI)
    val xmlContent = String(bomPomFile.contentsToByteArray())

    val versions =
      runReadAction {
        val xmlFile =
          PsiFileFactory.getInstance(project)
            .createFileFromText("bom.pom", XmlFileType.INSTANCE, xmlContent) as? XmlFile
            ?: return@runReadAction null

        val rootTag = xmlFile.rootTag ?: return@runReadAction null

        // Find <dependencyManagement><dependencies> section
        val dependencyManagement =
          rootTag.findFirstSubTag("dependencyManagement") ?: return@runReadAction null
        val dependencies =
          dependencyManagement.findFirstSubTag("dependencies") ?: return@runReadAction null

        val versionMap = mutableMapOf<String, String>()
        for (dependency in dependencies.findSubTags("dependency")) {
          val artifactId = dependency.findFirstSubTag("artifactId")?.value?.text
          val version = dependency.findFirstSubTag("version")?.value?.text

          if (artifactId != null && version != null) {
            versionMap[artifactId] = version
          }
        }
        versionMap
      } ?: return null

    // Cache the result
    bomVersionCache[cacheKey] = versions
    return versions
  }

  /**
   * Extracts the package attribute from AndroidManifest.xml inside an AAR file. AAR files are ZIP
   * archives containing AndroidManifest.xml at the root.
   */
  fun extractPackageFromAar(project: Project, aarFile: VirtualFile): String? {
    try {
      // AAR files are ZIP archives - read directly as a ZIP file
      val aarPath = aarFile.canonicalPath ?: aarFile.path.removePrefix("file://")
      val zipFile = java.util.zip.ZipFile(aarPath)

      zipFile.use { zip ->
        // Find AndroidManifest.xml entry
        val manifestEntry = zip.getEntry("AndroidManifest.xml") ?: return null

        // Read the manifest content
        val manifestContent = zip.getInputStream(manifestEntry).use { it.readBytes() }
        val xmlContent = String(manifestContent)

        // Parse as XML using PsiFileFactory (requires read action for PSI access)
        return runReadAction {
          val xmlFile =
            PsiFileFactory.getInstance(project)
              .createFileFromText("AndroidManifest.xml", XmlFileType.INSTANCE, xmlContent)
              as? XmlFile ?: return@runReadAction null

          val rootTag = xmlFile.rootTag ?: return@runReadAction null
          rootTag.getAttribute("package")?.value
        }
      }
    } catch (e: Exception) {
      logger.warn("Failed to extract package from AAR: ${aarFile.path}", e)
    }
    return null
  }
}
