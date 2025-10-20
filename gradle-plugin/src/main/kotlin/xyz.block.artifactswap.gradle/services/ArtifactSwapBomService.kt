package xyz.block.artifactswap.gradle.services

import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream

// Service to parse local BOM file once per gradle build
abstract class ArtifactSwapBomService : BuildService<ArtifactSwapBomService.Parameters> {
  private companion object {
    val logger: Logger = Logging.getLogger(ArtifactSwapBomService::class.java)
  }

  interface Parameters : BuildServiceParameters {
    /**
     * The Maven group ID used for artifact swap dependencies.
     */
    val mavenGroup: Property<String>

    /**
     * The version of the BOM to use.
     */
    val bomVersion: Property<String>

    /**
     * The path to the local Maven repository.
     */
    val localRepositoryPath: Property<File>
  }

  object KEY : SharedServiceKey<ArtifactSwapBomService, ArtifactSwapBomService.Parameters>("artifactSwapBom")

  private val bomFile: Path
    get() {
      val bomVersion = parameters.bomVersion.get()
      val mavenGroup = parameters.mavenGroup.get()
      val repoPath = parameters.localRepositoryPath.get().toPath()

      return repoPath
        .resolve(mavenGroup.replace(".", "/"))
        .resolve("bom/$bomVersion/bom-$bomVersion.pom")
    }

  val bomVersionMap by lazy {
    if (bomFile.exists()) {
      val pom = bomFile.inputStream().use { XmlSlurper().parse(it) }
      // https://maven.apache.org/pom.html
      val dependencyManagement = pom.getProperty("dependencyManagement") as GPathResult
      val dependencies = dependencyManagement.getProperty("dependencies") as GPathResult
      val dependencySequence = dependencies.children().asSequence().filterIsInstance<GPathResult>()
      dependencySequence
        .associate { it.getProperty("artifactId").toString() to it.getProperty("version").toString() }
    } else {
      logger.error("Artifact Swap bom does not exist: {}", bomFile)
      emptyMap()
    }
  }
}

val SharedServices.artifactSwapBomService get() = get(ArtifactSwapBomService.KEY)
