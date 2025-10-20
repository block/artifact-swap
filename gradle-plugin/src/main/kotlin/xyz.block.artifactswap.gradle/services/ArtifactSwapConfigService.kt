package xyz.block.artifactswap.gradle.services

import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Build service that holds the configuration for Artifact Swap.
 * This is separate from the BOM service to keep concerns separated.
 */
abstract class ArtifactSwapConfigService : BuildService<ArtifactSwapConfigService.Parameters> {

  interface Parameters : BuildServiceParameters {
    /**
     * The Maven group ID used for artifact swap dependencies.
     */
    val mavenGroup: Property<String>
  }

  object KEY : SharedServiceKey<ArtifactSwapConfigService, ArtifactSwapConfigService.Parameters>("artifactSwapConfig")

  /**
   * The configured Maven group for artifact swap dependencies.
   */
  val mavenGroup: String
    get() = parameters.mavenGroup.get()
}

val SharedServices.artifactSwapConfigService get() = get(ArtifactSwapConfigService.KEY)
