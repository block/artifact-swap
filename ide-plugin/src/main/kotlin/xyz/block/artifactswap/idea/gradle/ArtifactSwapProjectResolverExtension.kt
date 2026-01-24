package xyz.block.artifactswap.idea.gradle

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ModuleData
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import xyz.block.artifactswap.model.ArtifactSwapModel

/**
 * Gradle project resolver extension that requests the [ArtifactSwapModel] during sync. This allows
 * the IDE to retrieve configuration from the Gradle build.
 */
class ArtifactSwapProjectResolverExtension : AbstractProjectResolverExtension() {
  companion object {
    val ARTIFACT_SWAP_MODEL_KEY = Key.create(ArtifactSwapModel::class.java, 1)
    val logger = Logger.getInstance(ArtifactSwapProjectResolverExtension::class.java)
  }

  override fun populateModuleExtraModels(
    gradleModule: IdeaModule,
    ideModule: DataNode<ModuleData>,
  ) {
    val model = resolverCtx.getExtraProject(gradleModule, ArtifactSwapModel::class.java)
    if (model != null) {
      ideModule.createChild(ARTIFACT_SWAP_MODEL_KEY, model)
    } else {
      logger.info("Artifact swap is not enabled for this project")
    }
    super.populateModuleExtraModels(gradleModule, ideModule)
  }

  override fun getExtraProjectModelClasses() = setOf(ArtifactSwapModel::class.java)
}
