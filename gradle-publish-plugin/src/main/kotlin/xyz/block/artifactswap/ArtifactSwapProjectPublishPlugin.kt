@file:Suppress("UnstableApiUsage")

package xyz.block.artifactswap

import com.android.build.api.dsl.LibraryExtension
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.file.DuplicatesStrategy.EXCLUDE
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.bundling.Jar
import xyz.block.artifactswap.gradle.artifactSwapCoordinates
import xyz.block.artifactswap.gradle.internal.artifactSwapConfig
import xyz.block.artifactswap.gradle.internal.isAndroidLibrary
import xyz.block.artifactswap.gradle.internal.isKotlin

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
public class ArtifactSwapProjectPublishPlugin : Plugin<Project> {
  public override fun apply(target: Project): Unit =
    target.run {
      val version = projectArtifactVersion ?: return@run

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
          val publication =
            configureArtifactSwapPublication(
              mavenPublishing,
              version,
              artifactSwapConfig.primaryArtifactsMavenGroup,
            )
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
      val sandbagsUrl = getArtifactRepositoryUrl()
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
    artifactPublishGroup: String,
  ): MavenPublication {
    val publication =
      mavenPublishing.publications.maybeCreate("projectArtifact", MavenPublication::class.java)

    // Automatically configure maven coordinates for sandbag
    publication.groupId = artifactPublishGroup
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
      url.set(repoUrl())
      scm { scm ->
        scm.connection.set(scmConnectionUrl())
        scm.developerConnection.set(scmDeveloperConnectionUrl())
        scm.url.set(repoUrl())
      }
    }
  }

  private fun Project.repoUrl(): Provider<String> =
    providers
      .gradleProperty("artifactswap.repoUrl")
      .orElse(providers.gradleProperty("square.repoUrl"))

  private fun Project.scmDeveloperConnectionUrl(): Provider<String> =
    providers
      .gradleProperty("artifactswap.scmDeveloperConnectionUrl")
      .orElse(providers.gradleProperty("square.scmDeveloperConnectionUrl"))

  private fun Project.scmConnectionUrl(): Provider<String> =
    providers
      .gradleProperty("artifactswap.scmConnectionUrl")
      .orElse(providers.gradleProperty("square.scmConnectionUrl"))

  private fun Project.getSandbagCredentials(): SandbagCredentials? {
    val username = getArtifactRepoUsername()
    val password = getArtifactRepoPassword()
    return if (username != null && password != null) {
      SandbagCredentials(username, password)
    } else {
      null
    }
  }

  private fun Project.getArtifactRepositoryUrl(): String {
    // Support legacy property name to ease migrations
    return providers
      .gradleProperty("artifactswap.artifactRepo.url")
      .orElse(providers.gradleProperty("square.sandbagsUrl"))
      .orNull
      ?: throw RuntimeException(
        "No artifact repository URL provided. Please set " +
          "`artifactswap.artifactRepo.url` in your root gradle.properties file."
      )
  }

  private fun Project.getArtifactRepoPassword(): String? {
    return providers
      .gradleProperty("artifactswap.artifactRepo.password")
      .orElse(providers.gradleProperty("square.artifactory.password"))
      .orElse(
        providers.provider {
          readArtifactoryTokenFromFile(
            tokenFileName = artifactSwapConfig.artifactoryPublisherTokenFileName
          )
        }
      )
      .orNull
  }

  private fun Project.readArtifactoryTokenFromFile(tokenFileName: String): String? {
    // Missing or null env var → just treat as "no token"
    val secretsPath: String = providers.environmentVariable("SECRETS_PATH").orNull ?: return null

    val tokenFile = file(secretsPath).resolve(tokenFileName)

    // Missing file / not a regular file → also "no token"
    if (!tokenFile.isFile)
      throw IllegalArgumentException(
        "Artifactory token file name is set but file is not found. Verify `SECRETS_PATH` env var is set and points to a valid directory, and token file name in that directory is correct. Searched path: $tokenFile"
      )

    return try {
      tokenFile.useLines { lines ->
        lines
          .firstOrNull() // empty file → null
          ?.trim()
          ?.takeIf { it.isNotEmpty() } // whitespace-only → null
      }
    } catch (e: IOException) {
      throw IOException(
        "Failed to read Artifactory token file from $tokenFile, verify file contains a single line with the token.",
        e,
      )
    } catch (e: SecurityException) {
      throw SecurityException(
        "Failed to read Artifactory token file from $tokenFile, check file permissions.",
        e,
      )
    }
  }

  private fun Project.getArtifactRepoUsername(): String? {
    return providers
      .gradleProperty("artifactswap.artifactRepo.username")
      .orElse(providers.gradleProperty("square.artifactory.username"))
      .orNull
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
