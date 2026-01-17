package xyz.block.artifactswap.idea.notification

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import xyz.block.artifactswap.idea.config.ArtifactSwapConfig
import xyz.block.artifactswap.idea.settings.ArtifactSwapSettings
import xyz.block.artifactswap.idea.settings.ArtifactSwapSettingsConfigurable
import xyz.block.artifactswap.idea.util.SourceFileFinder

/**
 * Manages popup notifications for swapped artifact navigation. Shows a balloon notification when a
 * user opens a file from a swapped artifact, offering to update their navigation preferences.
 */
object SwappedArtifactPopupNotifier {

  private val logger = Logger.getInstance(SwappedArtifactPopupNotifier::class.java)

  // Track which files we've already shown notifications for in this session
  // to avoid spamming the user
  private val notifiedFiles = mutableSetOf<String>()

  /**
   * Shows a notification asking if the user wants to always navigate to source files instead of
   * binary artifacts.
   */
  fun showNavigationSuggestion(project: Project, file: VirtualFile) {
    val settings = ArtifactSwapSettings.getInstance()
    // If artifactswap is not enabled in the project, do nothing
    val config = ArtifactSwapConfig.fromProject(project) ?: return

    // Check if we should show the notification
    if (!settings.shouldShowNotification()) {
      return
    }

    // Don't show notification multiple times for the same file in this session
    val filePath = file.path
    if (filePath in notifiedFiles) {
      return
    }

    // Check if this is a swapped artifact file
    if (!config.isSwappedArtifactPath(filePath)) {
      return
    }

    // Check if source file exists
    val sourceFile = SourceFileFinder.findSourceFile(project, filePath, config)
    if (sourceFile == null) {
      logger.info("Source file not found for: $filePath")
      return
    }

    // Mark this file as notified
    notifiedFiles.add(filePath)

    // Create and show the notification
    val notification = createNotification(project, sourceFile)
    notification.notify(project)
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
      object : NotificationAction("Always Navigate to Source") {
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
      object : NotificationAction("Don't Show Again") {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          val settings = ArtifactSwapSettings.getInstance()
          settings.dismissNotification()
          notification.expire()
        }
      }
    )

    return notification
  }

  /**
   * Reset the session-specific notification tracking. Useful for testing or when settings are
   * changed.
   */
  fun resetSessionTracking() {
    notifiedFiles.clear()
  }
}
