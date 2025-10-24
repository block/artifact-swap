@file:Suppress("UnstableApiUsage")

package xyz.block.artifactswap

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.file.DuplicatesStrategy.EXCLUDE
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.bundling.Jar
import xyz.block.gradle.isAndroid
import xyz.block.gradle.isKotlin
import xyz.block.gradle.sandbagVersion
import xyz.block.gradle.toSandbagArtifact

/**
 * Artifact Swap project publish plugin for sandbags. This plugin is responsible for configuring
 * Maven publishing with sandbag-specific settings when sandbag publishing is enabled.
 *
 * This plugin extracts the sandbag publishing logic from PublishPlugin and AndroidLibJavaPlugin
 * to centralize artifact swap publishing concerns.
 *
 * For reference and searchability, the ID of this plugin is `xyz.block.artifactswap.publish`.
 */
@Suppress("unused")
class ArtifactSwapProjectPublishPlugin : Plugin<Project> {

  override fun apply(target: Project): Unit = target.run {
    pluginManager.apply("maven-publish")
    extensions.getByType(PublishingExtension::class.java).also { mavenPublishing ->
      configureSandbagRepository(mavenPublishing)
      configureSandbagPublication(mavenPublishing)
      mavenPublishing.publications.withType(MavenPublication::class.java)
        .configureEach { publication ->
          configureSandbagPom(publication.pom)
        }
    }

    tasks.withType(PublishToMavenRepository::class.java).configureEach {
      it.notCompatibleWithConfigurationCache("See https://github.com/gradle/gradle/issues/13468")
    }

    // Create a simple alias task for publishing
    tasks.register("publishToArtifactory") {
      it.dependsOn(tasks.named("publishAllPublicationsToSandbagRepository"))
    }
  }

  private fun Project.configureSandbagRepository(mavenPublishing: PublishingExtension) = with(mavenPublishing) {
    val sandbagsUrl = providers.gradleProperty("square.sandbagsUrl").get()
    repositories { handler ->
      handler.maven { repo ->
        repo.name = "sandbag"
        repo.url = uri(sandbagsUrl)

        with(getSandbagCredentials()) {
          if (username != null && password != null) {
            repo.credentials(PasswordCredentials::class.java) { creds ->
              creds.username = username
              creds.password = password
            }
          }
        }
      }
    }
  }

  private fun Project.configureSandbagPublication(mavenPublishing: PublishingExtension) {
    val publication = mavenPublishing.publications
      .maybeCreate("maven", MavenPublication::class.java)

    // Automatically configure maven coordinates for sandbag
    publication.groupId = ARTIFACT_SWAP_MAVEN_GROUP
    publication.artifactId = path.toSandbagArtifact
    publication.version = sandbagVersion

    // For non-Android projects, automatically configure the java component and sources
    if (!isAndroid) {
      publication.from(components.getByName("java"))
      addSourcesArtifact(publication)
    }
  }

  private fun Project.addSourcesArtifact(publication: MavenPublication) {
    when {
      // Android sources should be handled by the `publishing` lambda provided by AGP
      isAndroid -> Unit
      isKotlin -> {
        // KGP provides the `kotlinSourcesJar` task with the "sources" classifier.
        // It is not added to the java component automatically,
        // so we need to add the artifact to the publication ourselves.
        publication.artifact(tasks.named("kotlinSourcesJar"))
      }

      else -> {
        tasks.withType(Jar::class.java).configureEach {
          it.duplicatesStrategy = EXCLUDE
        }
        // If the project doesn't have Kotlin, the java sources can be added to the "java" component
        // and the maven publication will pick them up automatically.
        // Those sources aren't added by default, though -- hence this call to `withSourcesJar()`.
        extensions.getByType(JavaPluginExtension::class.java).withSourcesJar()
      }
    }
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

  private fun Project.getSandbagCredentials(): SandbagCredentials {
    val username = providers.gradleProperty("square.artifactory.username").orNull
    val password = providers.gradleProperty("square.artifactory.password").orNull
    return SandbagCredentials(username, password)
  }

  private data class SandbagCredentials(
    val username: String?,
    val password: String?
  )
}
