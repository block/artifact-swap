package com.squareup.register.artifactsync

import com.squareup.gradle.services.SharedServiceKey
import com.squareup.gradle.services.SharedServices
import com.squareup.register.artifactsync.ArtifactSyncBomService.Parameters
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream


// Service to parse local BOM file once per sync
abstract class ArtifactSyncBomService : BuildService<Parameters> {
  private companion object {
    val logger: Logger = Logging.getLogger(ArtifactSyncBomService::class.java)
  }

  interface Parameters : BuildServiceParameters {
    val bomVersion: Property<String>
  }

  object KEY : SharedServiceKey<ArtifactSyncBomService, Parameters>("artifactSyncBom")

  private val bomFile: Path
    get() {
      val bomVersion = parameters.bomVersion.get()
      return Path(System.getProperty("user.home"))
        .resolve(".m2/repository")
        .resolve(ARTIFACT_SYNC_MAVEN_GROUP.replace(".", "/"))
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
      logger.error("Artifact sync bom does not exist: {}", bomFile)
      emptyMap()
    }
  }
}

val SharedServices.artifactSyncBomService get() = get(ArtifactSyncBomService.KEY)
