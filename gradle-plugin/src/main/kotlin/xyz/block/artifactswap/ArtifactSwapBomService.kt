package xyz.block.artifactswap

import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import xyz.block.artifactswap.ArtifactSwapBomService.Parameters
import xyz.block.gradle.services.SharedServiceKey
import xyz.block.gradle.services.SharedServices

// Service to parse local BOM file once per sync
public abstract class ArtifactSwapBomService : BuildService<Parameters> {
  private companion object {
    val logger: Logger = Logging.getLogger(ArtifactSwapBomService::class.java)
  }

  public interface Parameters : BuildServiceParameters {
    public val bomVersion: Property<String>
    public val artifactSwapMavenGroup: Property<String>
    /** Path to the local Maven repository directory. */
    public val mavenLocalDirectory: Property<String>
  }

  internal object KEY : SharedServiceKey<ArtifactSwapBomService, Parameters>("artifactSyncBom")

  private val bomFile: Path
    get() {
      val bomVersion = parameters.bomVersion.get()
      val mavenLocalDir =
        Path.of(
          parameters.mavenLocalDirectory
            .get()
            .replace("\${user.home}", System.getProperty("user.home"))
        )
      return mavenLocalDir
        .resolve(parameters.artifactSwapMavenGroup.get().replace(".", "/"))
        .resolve("bom/$bomVersion/bom-$bomVersion.pom")
    }

  public val bomVersionMap: Map<String, String> by lazy {
    if (bomFile.exists()) {
      val pom = bomFile.inputStream().use { XmlSlurper().parse(it) }
      // https://maven.apache.org/pom.html
      val dependencyManagement = pom.getProperty("dependencyManagement") as GPathResult
      val dependencies = dependencyManagement.getProperty("dependencies") as GPathResult
      val dependencySequence = dependencies.children().asSequence().filterIsInstance<GPathResult>()
      dependencySequence.associate {
        it.getProperty("artifactId").toString() to it.getProperty("version").toString()
      }
    } else {
      logger.error("Artifact sync bom does not exist: {}", bomFile)
      emptyMap()
    }
  }
}

internal val SharedServices.artifactSyncBomService
  get() = get(ArtifactSwapBomService.KEY)
