package xyz.block.artifactswap.idea.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.panel
import xyz.block.artifactswap.idea.settings.ArtifactSwapSettings.NavigationBehavior.JUMP_TO_BINARY
import xyz.block.artifactswap.idea.settings.ArtifactSwapSettings.NavigationBehavior.JUMP_TO_SOURCE

/** Settings UI for the Artifact Swap plugin. */
class ArtifactSwapSettingsConfigurable : BoundConfigurable("Artifact Swap") {

  private val settings = ArtifactSwapSettings.getInstance()

  private var navigationBehavior: ArtifactSwapSettings.NavigationBehavior = JUMP_TO_SOURCE

  override fun createPanel(): DialogPanel {
    // Initialize from settings
    navigationBehavior = settings.navigationBehavior

    return panel {
      group("Navigation Behavior") {
        row { label("When navigating to classes in swapped artifacts:") }

        buttonsGroup {
            row { radioButton(JUMP_TO_BINARY.displayName, JUMP_TO_BINARY) }
            row {
              comment(
                "Shows the decompiled class from the JAR/AAR. Use the notification banner to jump to source."
              )
            }

            row { radioButton(JUMP_TO_SOURCE.displayName, JUMP_TO_SOURCE) }
            row { comment("Automatically navigates to the source file in your project.") }
          }
          .bind(this@ArtifactSwapSettingsConfigurable::navigationBehavior)
      }
    }
  }

  override fun apply() {
    super.apply()
    settings.navigationBehavior = navigationBehavior
  }

  override fun reset() {
    navigationBehavior = settings.navigationBehavior
    super.reset()
  }

  override fun isModified(): Boolean {
    return navigationBehavior != settings.navigationBehavior || super.isModified()
  }
}
