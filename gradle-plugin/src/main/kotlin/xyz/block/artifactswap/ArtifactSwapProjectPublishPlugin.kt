@file:Suppress("UnstableApiUsage")

package xyz.block.artifactswap

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
import xyz.block.gradle.artifactVersion
import xyz.block.gradle.isAndroid
import xyz.block.gradle.isKotlin
import xyz.block.gradle.services.services
import xyz.block.gradle.toProjectArtifactName

/**
 * Artifact Swap project publish plugin for project artifacts. This plugin is responsible for
 * configuring Maven publishing with artifact-swap-specific settings when artifact publishing is
 * enabled.
 *
 * For reference and searchability, the ID of this plugin is `xyz.block.artifactswap.publish`.
 */
@Suppress("unused")
class ArtifactSwapProjectPublishPlugin : Plugin<Project> {

  private lateinit var configService: ArtifactSwapConfigService

  override fun apply(target: Project): Unit = target.run {
    val version = artifactVersion ?: return@run

    configService = gradle.services.artifactSwapConfigService

    pluginManager.apply("maven-publish")
    extensions.getByType(PublishingExtension::class.java).also { mavenPublishing ->
      val repo = configureArtifactRepository(mavenPublishing)

      // Other plugins configure the components to be published, so we have to configure them
      // after those plugins run
      afterEvaluate {
        val publication = configureArtifactPublication(mavenPublishing, version)
        createPublishAliasTask(repo, publication)
      }
    }

    tasks.withType(PublishToMavenRepository::class.java).configureEach {
      it.notCompatibleWithConfigurationCache("See https://github.com/gradle/gradle/issues/13468")
    }
  }

  private fun Project.configureArtifactRepository(
    mavenPublishing: PublishingExtension,
  ): MavenArtifactRepository =
    with(mavenPublishing) {
      val repoUrl = configService.parameters.repoUrl.get()
      return repositories.maven { repo ->
        repo.name = "artifactSwap"
        repo.url = uri(repoUrl)

        val username = configService.parameters.repoUsername.orNull
        val password = configService.parameters.repoPassword.orNull
        if (username != null && password != null) {
          repo.credentials(PasswordCredentials::class.java) { creds ->
            creds.username = username
            creds.password = password
          }
        }
      }
    }

  private fun Project.configureArtifactPublication(
    mavenPublishing: PublishingExtension,
    version: String
  ): MavenPublication {
    val publication = mavenPublishing.publications.maybeCreate("projectArtifact", MavenPublication::class.java)

    // Automatically configure maven coordinates for artifact
    publication.groupId = ARTIFACT_SWAP_MAVEN_GROUP
    publication.artifactId = path.toProjectArtifactName
    publication.version = version

    // For non-Android projects, automatically configure the java component and sources
    if (!isAndroid) {
      publication.from(components.getByName("java"))
      addSourcesArtifact(publication)
    }

    configureArtifactPom(publication.pom)

    return publication
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

  private fun Project.configureArtifactPom(pom: MavenPom) {
    with(pom) {
      name.set(project.name)
      description.set("Artifact for ${project.name} in build ${project.isolated.rootProject.name}")
      url.set(configService.parameters.repoUrl)
    }
  }

  private fun Project.createPublishAliasTask(repo: MavenArtifactRepository, publication: MavenPublication) {
    val pubName = publication.name.replaceFirstChar { it.uppercase() }
    val repoName = repo.name.replaceFirstChar { it.uppercase() }
    val publishTaskName = "publish${pubName}PublicationTo${repoName}Repository"

    tasks.register("publishTo${repoName}Repository") {
      it.dependsOn(tasks.named(publishTaskName))
    }
  }
}
