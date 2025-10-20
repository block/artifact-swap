package xyz.block.artifactswap.gradle

import xyz.block.artifactswap.gradle.services.ArtifactSwapBomService
import xyz.block.artifactswap.gradle.services.ArtifactSwapConfigService
import xyz.block.artifactswap.gradle.services.services
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import java.io.File

/**
 * Settings plugin for Artifact Swap. Apply this in settings.gradle.kts:
 *
 * ```
 * plugins {
 *   id("xyz.block.artifactswap")
 * }
 *
 * artifactSwap {
 *   mavenGroup.set("com.example.artifacts")
 *   bomVersion.set("1.0.0")
 *   localRepositoryPath.set(file("~/.m2/repository"))
 * }
 * ```
 *
 * This plugin will automatically apply [ArtifactSwapProjectPlugin] to all projects.
 */
@Suppress("unused")
class ArtifactSwapSettingsPlugin : Plugin<Settings> {
  override fun apply(settings: Settings) = settings.run {
    // Register the configuration extension
    val extension = extensions.create("artifactSwap", ArtifactSwapExtension::class.java)

    // Set default values
    extension.localRepositoryPath.convention(
      File(System.getProperty("user.home"), ".m2/repository")
    )

    // Register the configuration service
    gradle.services.register(ArtifactSwapConfigService.KEY) { spec ->
      spec.parameters.mavenGroup.set(extension.mavenGroup)
    }

    // Register the BOM service with configuration from the extension
    gradle.services.register(ArtifactSwapBomService.KEY) { spec ->
      spec.parameters.mavenGroup.set(extension.mavenGroup)
      spec.parameters.bomVersion.set(extension.bomVersion)
      spec.parameters.localRepositoryPath.set(extension.localRepositoryPath)
    }

    // Auto-apply the project plugin to all projects
    gradle.allprojects { project ->
      project.plugins.apply(ArtifactSwapProjectPlugin::class.java)
    }
  }
}
