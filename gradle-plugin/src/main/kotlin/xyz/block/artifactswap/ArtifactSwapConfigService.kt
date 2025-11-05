package xyz.block.artifactswap

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import xyz.block.gradle.services.SharedServiceKey
import xyz.block.gradle.services.SharedServices
import java.io.File

/**
 * Build service that holds artifact swap publishing configuration. This allows the configuration to
 * be set at settings time and accessed in projects in a configuration-cache compatible way.
 */
abstract class ArtifactSwapConfigService : BuildService<ArtifactSwapConfigService.Params> {

  interface Params : BuildServiceParameters {
    val artifactHashFile: RegularFileProperty
    val repoUrl: Property<String>
    val repoUsername: Property<String>
    val repoPassword: Property<String>
  }

  internal object KEY : SharedServiceKey<ArtifactSwapConfigService, Params>("artifactSwapConfig")
}

internal val SharedServices.artifactSwapConfigService
  get() = get(ArtifactSwapConfigService.KEY)
