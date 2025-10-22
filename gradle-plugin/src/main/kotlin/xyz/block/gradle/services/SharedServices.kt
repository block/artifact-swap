@file:Suppress("UnstableApiUsage")

package xyz.block.gradle.services

import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.api.services.BuildServiceSpec

internal val Gradle.services: SharedServices
  get() = SharedServices(this)

/**
 * Wrapper around [BuildServiceRegistry] providing better registration and reference of services.
 *
 * These services are shared by *all* Gradle projects by default, and should be careful to properly
 * ensure thread-safe access where necessary.
 */
internal class SharedServices(private val gradle: Gradle) {

  /**
   * Registers a new service by the provided [key] and allows lazy configuration of the service.
   *
   * Only the first service registered per [name][SharedServiceKey.name] will be used for lookup.
   * It is an error to use the same name for multiple services of different types.
   */
  inline fun <reified T : BuildService<P>, P : BuildServiceParameters> register(
    key: SharedServiceKey<T, P>,
    noinline configure: (BuildServiceSpec<P>) -> Unit = { }
  ): Provider<T> {
    return register(key, T::class.java, configure)
  }

  /** See [register(key, configure)][register]. */
  fun <T : BuildService<P>, P : BuildServiceParameters> register(
    key: SharedServiceKey<T, P>,
    clazz: Class<T>,
    configure: (BuildServiceSpec<P>) -> Unit = { }
  ): Provider<T> {
    return gradle.sharedServices.registerIfAbsent(key.name, clazz) { configure(it) }
  }

  /** Returns the service registered with the matching [key]. */
  fun <T : BuildService<P>, P : BuildServiceParameters> get(key: SharedServiceKey<T, P>): T {
    @Suppress("UNCHECKED_CAST")
    return gradle.sharedServices.registrations.getAt(key.name).service.get() as T
  }

  fun <T : BuildService<P>, P : BuildServiceParameters> provider(
    key: SharedServiceKey<T, P>
  ): Provider<T> {
    @Suppress("UNCHECKED_CAST")
    return gradle.sharedServices.registrations.getAt(key.name).service as Provider<T>
  }
}

/** Key used for service registration and lookup. */
internal abstract class SharedServiceKey<T : BuildService<P>, P : BuildServiceParameters>(
  val name: String
)
