package xyz.block.artifactswap.idea.notification

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import javax.swing.JComponent
import xyz.block.artifactswap.idea.config.ArtifactPathInfo
import xyz.block.artifactswap.idea.config.ArtifactSwapConfig
import xyz.block.artifactswap.idea.navigation.ArtifactSwapGotoDeclarationHandler
import xyz.block.artifactswap.idea.util.SourceFileFinder

/**
 * An [EditorNotificationProvider] that shows a notification banner when a user is viewing a file
 * inside a swapped artifact JAR.
 *
 * This serves as a fallback mechanism when the [ArtifactSwapGotoDeclarationHandler] doesn't
 * intercept the navigation (e.g., when navigating from find usages or other means).
 *
 * The banner provides a one-click way to navigate to the corresponding source file in the project.
 */
class SwappedArtifactNotificationProvider : EditorNotificationProvider, DumbAware {

  private val logger = Logger.getInstance(SwappedArtifactNotificationProvider::class.java)

  override fun collectNotificationData(
    project: Project,
    file: VirtualFile,
  ): Function<in FileEditor, out JComponent?>? {
    // Do nothing if artifactswap is not enabled in the project
    val config = ArtifactSwapConfig.fromProject(project) ?: return null
    val filePath = file.path

    // Check if this file is inside a swapped artifact
    if (!config.isSwappedArtifactPath(filePath)) {
      return null
    }

    // Get information about the source file
    val sourceFileInfo = SourceFileFinder.getSourceFileInfo(project, filePath, config)
    if (sourceFileInfo == null) {
      logger.warn("Could not get source file info for: $filePath")
      return null
    }

    return Function { _ -> createNotificationPanel(project, sourceFileInfo) }
  }

  private fun createNotificationPanel(
    project: Project,
    sourceFileInfo: ArtifactPathInfo,
  ): EditorNotificationPanel {
    val panel = EditorNotificationPanel()
    panel.text = "This file is from a swapped artifact (${sourceFileInfo.projectPath})"

    val sourceFile = sourceFileInfo.sourceFile
    if (sourceFile != null) {
      panel.createActionLabel("Open source file") {
        // Navigate to source file
        logger.info("Opening source file: ${sourceFile.path}")
        FileEditorManager.getInstance(project).openFile(sourceFile, true)
      }
    } else {
      panel.createActionLabel("Open project") {
        // Try to navigate to the module directory at least
        val basePath = project.basePath
        if (basePath != null) {
          val moduleDir =
            LocalFileSystem.getInstance().findFileByPath("$basePath/${sourceFileInfo.moduleDir}")
          if (moduleDir != null) {
            FileEditorManager.getInstance(project).openFile(moduleDir, true)
          }
        }
      }
    }

    return panel
  }
}
