@file:Suppress("unused")

package xyz.block.artifactswap.dsl

import org.gradle.api.Action
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.initialization.Settings
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import java.io.File
import javax.inject.Inject

/**
 * Extension for configuring Artifact Swap publishing credentials.
 *
 * Example usage in settings.gradle:
 * ```
 * artifactSwap {
 *   publishing {
 *     enabled true
 *     repo {
 *       url "https://repo.example.com/maven"
 *       username "user"
 *       password "password"
 *     }
 *   }
 * }
 * ```
 */
abstract class ArtifactSwapExtension
@Inject
constructor(objects: ObjectFactory, providers: ProviderFactory) {
  companion object {
    const val NAME = "artifactSwap"

    fun of(settings: Settings): ArtifactSwapExtension {
      return settings.extensions.create(NAME, ArtifactSwapExtension::class.java)
    }
  }

  /** Publishing configuration. */
  val publishing: PublishingConfiguration =
    objects.newInstance(PublishingConfiguration::class.java, providers)

  fun publishing(action: Action<PublishingConfiguration>) {
    action.execute(publishing)
  }

  /** Configuration for publishing artifacts. */
  abstract class PublishingConfiguration
  @Inject
  constructor(objects: ObjectFactory, providers: ProviderFactory) {

    internal val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    internal val artifactHashFile: RegularFileProperty = objects.fileProperty()
    internal val repo: RepoConfiguration = objects.newInstance(RepoConfiguration::class.java, providers)

    /** Whether artifact publishing is enabled. */
    fun enabled(enabled: Provider<Boolean>) {
      this.enabled.set(enabled)
      this.enabled.disallowChanges()
    }

    /** The artifact hash file containing project versions. */
    fun artifactHashFile(file: Provider<RegularFile>) {
      this.artifactHashFile.set(file)
      this.artifactHashFile.disallowChanges()
    }

    /** Repository configuration. */
    fun repo(action: Action<RepoConfiguration>) {
      action.execute(repo)
    }
  }

  /** Configuration for repository credentials and URL. */
  abstract class RepoConfiguration
  @Inject
  constructor(private val objects: ObjectFactory, private val providers: ProviderFactory) {

    internal val url: Property<String> = objects.property(String::class.java)
    internal val username: Property<String> = objects.property(String::class.java)
    internal val password: Property<String> = objects.property(String::class.java)

    /** The repository URL for publishing artifacts. */
    fun url(url: String) {
      this.url.set(url)
      this.url.disallowChanges()
    }

    /** The repository username for publishing artifacts. */
    fun username(username: String) {
      this.username.set(username)
      this.username.disallowChanges()
    }

    /** The repository password for publishing artifacts. */
    fun password(password: Provider<String>) {
      this.password.set(password)
      this.password.disallowChanges()
    }

    /** Gets the credentials if both username and password are available. */
    internal fun getCredentials(): PasswordCredentials? {
      val username = username.orNull
      val password = password.orNull?.trim()

      return if (username != null && password != null) {
        objects.newInstance(PasswordCredentials::class.java).apply {
          this.username = username
          this.password = password
        }
      } else {
        null
      }
    }
  }
}
