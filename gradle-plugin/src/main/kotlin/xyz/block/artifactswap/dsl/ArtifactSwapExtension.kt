@file:Suppress("unused")

package xyz.block.artifactswap.dsl

import javax.inject.Inject
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider

/**
 * Extension for configuring Artifact Swap.
 *
 * Example usage in settings.gradle:
 * ```
 * artifactSwap {
 *   enabled(true)
 *   // or use a provider:
 *   enabled(providers.gradleProperty("artifactswap.enabled").map { it.toBoolean() })
 * }
 * ```
 */
public abstract class ArtifactSwapExtension
@Inject
constructor(gradle: Gradle, private val dslService: ArtifactSwapDslService) {

  public companion object {
    public const val NAME: String = "artifactSwap"

    @JvmStatic
    internal fun create(
      settings: Settings,
      dslService: ArtifactSwapDslService,
    ): ArtifactSwapExtension {
      return settings.extensions.create(NAME, ArtifactSwapExtension::class.java, dslService)
    }

    @JvmStatic
    public fun of(settings: Settings): ArtifactSwapExtension {
      return settings.extensions.getByType(ArtifactSwapExtension::class.java)
    }
  }

  /**
   * Enable or disable artifact swap
   *
   * This can still be overridden by the `artifactswap.enabled` gradle property
   */
  public fun enabled(enabled: Boolean): Unit = dslService.enabled(enabled)

  /**
   * Enable or disable artifact swap
   *
   * This can still be overridden by the `artifactswap.enabled` gradle property
   */
  public fun enabled(enabled: Provider<Boolean>): Unit = dslService.enabled(enabled)
}
