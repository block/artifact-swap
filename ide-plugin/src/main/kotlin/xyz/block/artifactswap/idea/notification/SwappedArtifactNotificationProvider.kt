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
import xyz.block.artifactswap.idea.navigation.ArtifactSwapGotoDeclarationHandler
import xyz.block.artifactswap.idea.util.SourceFileFinder
import xyz.block.artifactswap.idea.util.artifactSwapModel
import xyz.block.artifactswap.model.ArtifactPathInfo
import xyz.block.artifactswap.model.isSwappedArtifactPath

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
    val model = project.artifactSwapModel ?: return null
    val filePath = file.path

    // Check if this file is inside a swapped artifact
    if (!model.isSwappedArtifactPath(filePath)) {
      return null
    }

    // Get information about the source file
    val (pathInfo, sourceFile) =
      SourceFileFinder.getSourceFileInfo(project, filePath, model) ?: return null

    return Function { _ -> createNotificationPanel(project, pathInfo, sourceFile) }
  }

  private fun createNotificationPanel(
    project: Project,
    artifactPathInfo: ArtifactPathInfo,
    sourceFile: VirtualFile?,
  ): EditorNotificationPanel {
    val panel = EditorNotificationPanel()
    panel.text = "This file is from a swapped artifact (${artifactPathInfo.projectPath})"

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
            LocalFileSystem.getInstance().findFileByPath("$basePath/${artifactPathInfo.moduleDir}")
          if (moduleDir != null) {
            FileEditorManager.getInstance(project).openFile(moduleDir, true)
          }
        }
      }
    }

    return panel
  }
}
