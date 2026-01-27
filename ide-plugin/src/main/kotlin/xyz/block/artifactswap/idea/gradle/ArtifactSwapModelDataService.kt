package xyz.block.artifactswap.idea.gradle

import com.intellij.openapi.diagnostic.Logger
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

  private val logger = Logger.getInstance(ArtifactSwapModelDataService::class.java)

  override fun getTargetDataKey(): Key<ArtifactSwapModel> =
    ArtifactSwapProjectResolverExtension.ARTIFACT_SWAP_MODEL_KEY

  override fun importData(
    toImport: Collection<DataNode<ArtifactSwapModel>>,
    projectData: ProjectData?,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider,
  ) {
    // Clear cache to avoid stale mappings from previous Gradle sync
    AarPackageCacheService.getInstance(project).clearCache()

    val service = ArtifactSwapService.getInstance(project)
    when (val data = toImport.firstOrNull()?.data) {
      null -> {
        logger.info("No ArtifactSwapModel found, clearing service")
        service.clearModel()
      }
      else -> {
        logger.info(
          "Updating service with model: mavenGroup=${data.mavenGroup}, bomVersion=${data.bomVersion}"
        )
        service.updateModel(data)

        // Trigger background scan of AARs to pre-populate the package cache
        logger.info("Starting background AAR scan")
        AarPackageCacheService.getInstance(project).scanAarsInBackground(data)
      }
    }
  }
}
