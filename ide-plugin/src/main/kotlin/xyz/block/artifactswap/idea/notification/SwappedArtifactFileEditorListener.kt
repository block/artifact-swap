package xyz.block.artifactswap.idea.notification

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile

/**
 * Listens for file opening and selection events and shows a notification if the user opens a file
 * from a swapped artifact. Only shows notifications for the currently active/selected editor tab.
 */
class SwappedArtifactFileEditorListener : FileEditorManagerListener {

  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    // Only show notification if this file is the currently selected file
    // (not just opened in a background tab)
    val selectedFile = source.selectedFiles.firstOrNull()
    if (selectedFile == file) {
      SwappedArtifactPopupNotifier.showNavigationSuggestion(source.project, file)
    }
  }

  override fun selectionChanged(event: FileEditorManagerEvent) {
    // Show notification when user switches to a different tab
    val newFile = event.newFile
    if (newFile != null) {
      SwappedArtifactPopupNotifier.showNavigationSuggestion(event.manager.project, newFile)
    }
  }
}
