package xyz.block.artifactswap.dsl

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceSpec
import xyz.block.artifactswap.dsl.ArtifactSwapDslService.Parameters
import xyz.block.gradle.isArtifactSwapEnabledByGradleProperty
import xyz.block.gradle.services.SharedServiceKey
import xyz.block.gradle.services.SharedServices

public abstract class ArtifactSwapDslService @Inject constructor(objects: ObjectFactory) :
  BuildService<Parameters> {
  public interface Parameters : BuildServiceParameters {
    public val enabledGradleProperty: Property<Boolean>
  }

  internal companion object {
    const val NAME = "artifactSwapDslService"

    fun of(
      gradle: Gradle,
      action: Action<BuildServiceSpec<Parameters>>,
    ): Provider<ArtifactSwapDslService> =
      gradle.sharedServices.registerIfAbsent(NAME, ArtifactSwapDslService::class.java, action)

    fun of(settings: Settings): Provider<ArtifactSwapDslService> =
      of(settings.gradle) { spec ->
        spec.parameters.enabledGradleProperty.set(
          settings.providers.isArtifactSwapEnabledByGradleProperty
        )
      }

    fun of(project: Project): Provider<ArtifactSwapDslService> =
      of(project.gradle) { spec ->
        spec.parameters.enabledGradleProperty.set(
          project.providers.isArtifactSwapEnabledByGradleProperty
        )
      }
  }

  internal object KEY : SharedServiceKey<ArtifactSwapDslService, Parameters>(NAME)

  private val enabledViaDsl: Property<Boolean> =
    objects.property(Boolean::class.java).unsetConvention()

  public fun enabled(enabled: Boolean) {
    this.enabledViaDsl.set(enabled)
    this.enabledViaDsl.disallowChanges()
  }

  public fun enabled(enabled: Provider<Boolean>) {
    this.enabledViaDsl.set(enabled)
    this.enabledViaDsl.disallowChanges()
  }

  public val enabled: Boolean
    get() = parameters.enabledGradleProperty.orElse(enabledViaDsl).getOrElse(true)
}

internal val SharedServices.dslService
  get() = get(ArtifactSwapDslService.KEY)
