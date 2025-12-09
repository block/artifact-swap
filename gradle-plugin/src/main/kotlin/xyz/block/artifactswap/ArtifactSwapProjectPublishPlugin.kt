@file:Suppress("UnstableApiUsage")

package xyz.block.artifactswap

import com.android.build.api.dsl.LibraryExtension
import java.net.InetAddress
import java.net.URI
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.file.DuplicatesStrategy.EXCLUDE
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.bundling.Jar
import xyz.block.gradle.artifactSwapCoordinates
import xyz.block.gradle.isAndroidLibrary
import xyz.block.gradle.isKotlin
import xyz.block.gradle.sandbagVersion

/**
 * Artifact Swap project publish plugin for sandbags. This plugin is responsible for configuring
 * Maven publishing with sandbag-specific settings when sandbag publishing is enabled.
 *
 * This plugin extracts the sandbag publishing logic from PublishPlugin and AndroidLibJavaPlugin to
 * centralize artifact swap publishing concerns.
 *
 * For reference and searchability, the ID of this plugin is `xyz.block.artifactswap.publish`.
 */
@Suppress("unused")
class ArtifactSwapProjectPublishPlugin : Plugin<Project> {
  override fun apply(target: Project): Unit =
    target.run {
      val version = sandbagVersion ?: return@run

      pluginManager.apply("maven-publish")

      // Configure android projects to publish a single debug variant library
      pluginManager.withPlugin("com.android.library") {
        val publishedVariantName = getPublishedVariantName()
        val agpLibraries = extensions.getByType(LibraryExtension::class.java)
        agpLibraries.publishing { singleVariant(publishedVariantName) { withSourcesJar() } }
      }

      extensions.getByType(PublishingExtension::class.java).also { mavenPublishing ->
        val repo = configureArtifactSwapRepository(mavenPublishing)

        // Other plugins configure the components to be published, so we have to configure them
        // after those plugins run
        afterEvaluate {
          val publication = configureArtifactSwapPublication(mavenPublishing, version)
          createPublishAliasTask(repo, publication)
        }
      }

      tasks.withType(PublishToMavenRepository::class.java).configureEach {
        it.notCompatibleWithConfigurationCache("See https://github.com/gradle/gradle/issues/13468")
      }
    }

  private fun Project.configureArtifactSwapRepository(
    mavenPublishing: PublishingExtension
  ): MavenArtifactRepository =
    with(mavenPublishing) {
      val sandbagsUrl = providers.gradleProperty("square.sandbagsUrl").get()
      return repositories.maven { repo ->
        repo.name = "artifactSwap"
        repo.url = uri(sandbagsUrl)
        // To support testing against non-https localhost
        repo.isAllowInsecureProtocol = isInsecureLocal(sandbagsUrl)

        getSandbagCredentials()?.apply {
          repo.credentials(PasswordCredentials::class.java) { creds ->
            creds.username = username
            creds.password = password
          }
        }
      }
    }

  private fun isInsecureLocal(url: String): Boolean {
    return try {
      val uri = URI(url.trim())

      // Only care about insecure protocol
      if (!uri.scheme.equals("http", ignoreCase = true)) {
        return false
      }

      val host = uri.host ?: return false

      // Fast path for localhost literal
      if (host.equals("localhost", ignoreCase = true)) {
        return true
      }

      // Resolve and check for loopback (covers 127.0.0.1, 127.x.x.x, ::1, etc.)
      val address = InetAddress.getByName(host)
      address.isLoopbackAddress
    } catch (e: Exception) {
      // If the URL is malformed, don't treat it as local/insecure
      false
    }
  }

  private fun Project.configureArtifactSwapPublication(
    mavenPublishing: PublishingExtension,
    version: String,
  ): MavenPublication {
    val publication =
      mavenPublishing.publications.maybeCreate("projectArtifact", MavenPublication::class.java)

    // Automatically configure maven coordinates for sandbag
    publication.groupId = ARTIFACT_SWAP_MAVEN_GROUP
    publication.artifactId = artifactSwapCoordinates
    publication.version = version

    when {
      isAndroidLibrary -> publishAndroidLibrary(publication)
      else -> publishJvmLibrary(publication)
    }

    configureSandbagPom(publication.pom)

    return publication
  }

  private fun Project.publishAndroidLibrary(publication: MavenPublication) {
    val publishedVariantName = getPublishedVariantName()
    publication.from(components.getByName(publishedVariantName))
  }

  private fun Project.publishJvmLibrary(publication: MavenPublication) {
    publication.from(components.getByName("java"))
    when {
      isKotlin -> {
        // KGP provides the `kotlinSourcesJar` task with the "sources" classifier.
        // It is not added to the java component automatically,
        // so we need to add the artifact to the publication ourselves.
        publication.artifact(tasks.named("kotlinSourcesJar"))
      }

      else -> {
        tasks.withType(Jar::class.java).configureEach { it.duplicatesStrategy = EXCLUDE }
        // If the project doesn't have Kotlin, the java sources can be added to the "java" component
        // and the maven publication will pick them up automatically.
        // Those sources aren't added by default, though -- hence this call to `withSourcesJar()`.
        extensions.getByType(JavaPluginExtension::class.java).withSourcesJar()
      }
    }
  }

  private fun Project.getPublishedVariantName(): String {
    val agpLibraries = extensions.getByType(LibraryExtension::class.java)
    return agpLibraries.productFlavors.firstOrNull { it.isDefault }?.let { it.name + "Debug" }
      ?: "debug"
  }

  private fun Project.configureSandbagPom(pom: MavenPom) {
    with(pom) {
      name.set(project.name)
      description.set("Sandbag for ${project.name} in build ${project.isolated.rootProject.name}")
      url.set(providers.gradleProperty("square.repoUrl"))
      scm { scm ->
        scm.connection.set(providers.gradleProperty("square.scmConnectionUrl"))
        scm.developerConnection.set(providers.gradleProperty("square.scmDeveloperConnectionUrl"))
        scm.url.set(providers.gradleProperty("square.repoUrl"))
      }
    }
  }

  private fun Project.getSandbagCredentials(): SandbagCredentials? {
    val username = providers.gradleProperty("square.artifactory.username").orNull
    val password = providers.gradleProperty("square.artifactory.password").orNull
    return if (username != null && password != null) {
      SandbagCredentials(username, password)
    } else {
      null
    }
  }

  private fun Project.createPublishAliasTask(
    repo: MavenArtifactRepository,
    publication: MavenPublication,
  ) {
    val pubName = publication.name.replaceFirstChar { it.uppercase() }
    val repoName = repo.name.replaceFirstChar { it.uppercase() }
    val publishTaskName = "publish${pubName}PublicationTo${repoName}Repository"

    tasks.register("publishTo${repoName}Repository") { it.dependsOn(tasks.named(publishTaskName)) }
  }

  private data class SandbagCredentials(val username: String, val password: String)
}
