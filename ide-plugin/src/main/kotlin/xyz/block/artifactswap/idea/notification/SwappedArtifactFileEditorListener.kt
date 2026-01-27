package xyz.block.artifactswap.idea.notification

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import xyz.block.artifactswap.idea.settings.ArtifactSwapSettings
import xyz.block.artifactswap.idea.settings.ArtifactSwapSettingsConfigurable
import xyz.block.artifactswap.idea.util.SourceFileFinder
import xyz.block.artifactswap.idea.util.artifactSwapModel
import xyz.block.artifactswap.model.isSwappedArtifactPath

/**
 * Listens for file opening and selection events and shows a notification if the user opens a file
 * from a swapped artifact. Only shows notifications for the currently active/selected editor tab.
 */
class SwappedArtifactFileEditorListener : FileEditorManagerListener {

  companion object {
    // Track whether we've shown the notification in this IDE session
    @Volatile private var hasShownNotificationThisSession = false
  }

  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    // Only show notification if this file is the currently selected file
    // (not just opened in a background tab)
    val selectedFile = source.selectedFiles.firstOrNull()
    if (selectedFile == file) {
      showNavigationSuggestion(source.project, file)
    }
  }

  override fun selectionChanged(event: FileEditorManagerEvent) {
    // Show notification when user switches to a different tab
    val newFile = event.newFile
    if (newFile != null) {
      showNavigationSuggestion(event.manager.project, newFile)
    }
  }

  private fun showNavigationSuggestion(project: Project, file: VirtualFile) {
    // Early exit if we've already shown the notification this session
    if (hasShownNotificationThisSession) return

    val settings = ArtifactSwapSettings.getInstance()
    // Check if user has permanently dismissed the notification
    if (!settings.shouldShowNotification()) return

    ApplicationManager.getApplication().executeOnPooledThread {
      locateArtifactAndShowNotification(project, file)
    }
  }

  private fun locateArtifactAndShowNotification(project: Project, file: VirtualFile) {
    // If artifactswap is not enabled in the project, do nothing
    val model = project.artifactSwapModel ?: return
    val filePath = file.path
    if (!model.isSwappedArtifactPath(filePath)) return
    val sourceFile =
      SourceFileFinder.findSourceFile(project, filePath, sourceElement = null, model) ?: return

    hasShownNotificationThisSession = true

    // Show notification on EDT
    ApplicationManager.getApplication().invokeLater {
      if (!project.isDisposed) {
        val notification = createNotification(project, sourceFile)
        notification.notify(project)
      }
    }
  }

  private fun createNotification(project: Project, sourceFile: VirtualFile): Notification {
    val notification =
      NotificationGroupManager.getInstance()
        .getNotificationGroup("Artifact Swap Notifications")
        .createNotification(
          "Open source file",
          "You're viewing a swapped artifact. Would you like to always navigate to source files instead?",
          NotificationType.INFORMATION,
        )

    // Action: Always navigate to source
    notification.addAction(
      object : NotificationAction("Always navigate to source") {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          val settings = ArtifactSwapSettings.getInstance()
          settings.navigationBehavior = ArtifactSwapSettings.NavigationBehavior.JUMP_TO_SOURCE

          // Open the source file immediately
          FileEditorManager.getInstance(project).openFile(sourceFile, true)

          notification.expire()
        }
      }
    )

    // Action: Open settings
    notification.addAction(
      object : NotificationAction("Settings...") {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          ShowSettingsUtil.getInstance()
            .showSettingsDialog(project, ArtifactSwapSettingsConfigurable::class.java)
          notification.expire()
        }
      }
    )

    // Action: Don't show again
    notification.addAction(
      object : NotificationAction("Don't show again") {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          val settings = ArtifactSwapSettings.getInstance()
          settings.dismissNotification()
          notification.expire()
        }
      }
    )

    return notification
  }
}
