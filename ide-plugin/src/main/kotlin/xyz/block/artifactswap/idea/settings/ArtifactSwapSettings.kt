package xyz.block.artifactswap.idea.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil

/** Persistent settings for the Artifact Swap plugin. */
@Service
@State(
  name = "xyz.block.artifactswap.idea.settings.ArtifactSwapSettings",
  storages = [Storage("ArtifactSwapSettings.xml")],
)
class ArtifactSwapSettings : PersistentStateComponent<ArtifactSwapSettings> {

  /** The navigation behavior when clicking on references to swapped artifacts. */
  var navigationBehavior: NavigationBehavior = NavigationBehavior.JUMP_TO_BINARY

  /** Whether the user has dismissed the navigation suggestion notification. */
  var navigationBehaviorPromptDismissed: Boolean = false

  /** Check if the notification should be shown. */
  fun shouldShowNotification(): Boolean {
    if (navigationBehaviorPromptDismissed) {
      return false
    }
    // Don't show notification if user has already set their preference
    if (navigationBehavior == NavigationBehavior.JUMP_TO_SOURCE) {
      return false
    }
    return true
  }

  fun dismissNotification() {
    navigationBehaviorPromptDismissed = true
  }

  override fun getState(): ArtifactSwapSettings = this

  override fun loadState(state: ArtifactSwapSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    fun getInstance(): ArtifactSwapSettings = service()
  }

  enum class NavigationBehavior(val displayName: String) {
    JUMP_TO_BINARY("Navigate to binary artifact (default)"),
    JUMP_TO_SOURCE("Navigate directly to source file"),
  }
}
