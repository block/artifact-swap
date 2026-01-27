package xyz.block.artifactswap.idea.gradle

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.project.Project
import xyz.block.artifactswap.model.ArtifactSwapModel

/**
 * Data service that processes [ArtifactSwapModel] after Gradle sync and updates the
 * [ArtifactSwapService] with the discovered configuration.
 */
class ArtifactSwapModelDataService : AbstractProjectDataService<ArtifactSwapModel, Void>() {

  override fun getTargetDataKey(): Key<ArtifactSwapModel> =
    ArtifactSwapProjectResolverExtension.ARTIFACT_SWAP_MODEL_KEY

  override fun importData(
    toImport: Collection<DataNode<ArtifactSwapModel>>,
    projectData: ProjectData?,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider,
  ) {
    val service = ArtifactSwapService.getInstance(project)
    when (val data = toImport.firstOrNull()?.data) {
      null -> service.clearModel()
      else -> service.updateModel(data)
    }
  }
}
