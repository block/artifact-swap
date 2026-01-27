package xyz.block.artifactswap.idea.gradle

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import xyz.block.artifactswap.model.ArtifactSwapModel

/**
 * Service that stores the [ArtifactSwapModel] from the most recent Gradle sync.
 *
 * This provides the authoritative source for artifact swap configuration after Gradle sync,
 * including the Maven group ID, BOM version, and included project paths.
 */
@Service(Service.Level.PROJECT)
class ArtifactSwapService : Disposable {

  private var _model: ArtifactSwapModel? = null

  /**
   * The artifact swap model from the most recent Gradle sync. Returns null if no sync has completed
   * yet or if the model was not available.
   */
  val model: ArtifactSwapModel?
    get() = _model

  /**
   * Update the artifact swap model from Gradle sync. Called by [ArtifactSwapModelDataService]
   * during project import.
   */
  fun updateModel(model: ArtifactSwapModel) {
    _model = model
  }

  /**
   * Check if Gradle sync has completed and we have model data. Returns true once at least one sync
   * has completed with a model.
   */
  val hasModel: Boolean
    get() = _model != null

  /** Clear the model (e.g., when build configuration changes). */
  fun clearModel() {
    _model = null
  }

  override fun dispose() {
    _model = null
  }

  companion object {
    @JvmStatic fun getInstance(project: Project): ArtifactSwapService = project.service()
  }
}
