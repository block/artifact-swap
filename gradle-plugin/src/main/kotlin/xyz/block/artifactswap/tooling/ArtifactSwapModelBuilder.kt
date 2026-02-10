package xyz.block.artifactswap.tooling

import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilder
import xyz.block.artifactswap.artifactSyncBomService
import xyz.block.artifactswap.dsl.ArtifactSwapDslService
import xyz.block.artifactswap.gradle.internal.artifactSwapConfig
import xyz.block.artifactswap.gradle.internal.isRootProject
import xyz.block.artifactswap.model.ArtifactSwapModel
import xyz.block.artifactswap.model.DefaultArtifactSwapModel
import xyz.block.gradle.services.services

/**
 * Tooling model builder that provides [ArtifactSwapModel] to the IDE via Gradle Tooling API.
 *
 * This allows the IDE plugin to retrieve configuration from the Gradle build, such as:
 * - Maven group ID for swapped artifacts
 * - BOM version for swapped artifacts
 */
public class ArtifactSwapModelBuilder : ToolingModelBuilder {
  override fun canBuild(modelName: String): Boolean {
    return modelName == ArtifactSwapModel::class.java.name
  }

  @Suppress("WRONG_NULLABILITY_FOR_JAVA_OVERRIDE")
  override fun buildAll(modelName: String, project: Project): ArtifactSwapModel? {
    // Only build for root project
    if (!project.isRootProject) return null

    // Check if enabled
    if (!ArtifactSwapDslService.of(project).get().enabled) return null

    val config = project.artifactSwapConfig
    val bomVersion = project.gradle.services.artifactSyncBomService.parameters.bomVersion.get()

    val resolvedMavenLocalDir =
      config.mavenLocalDirectory.replace("\${user.home}", System.getProperty("user.home"))

    return DefaultArtifactSwapModel(
      mavenGroup = config.primaryArtifactsMavenGroup,
      bomVersion = bomVersion,
      mavenLocalDirectory = resolvedMavenLocalDir,
    )
  }
}
