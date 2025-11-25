package xyz.block.artifactswap.convention.plugin

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.MavenPublishPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class PublishPlugin : Plugin<Project> {
  override fun apply(target: Project): Unit = target.run {
    plugins.apply(MavenPublishPlugin::class.java)
    extensions.getByType(MavenPublishBaseExtension::class.java).run {
      publishToMavenCentral(automaticRelease = true)

      val hasSigningKey = providers.gradleProperty("signingInMemoryKey")
        .isPresent
      if (hasSigningKey) {
        signAllPublications()
      }

      pom { pom ->
        pom.name.set("Artifact Swap ${project.name}")
        pom.description.set("Gradle tooling that helps manage large builds")
        pom.inceptionYear.set("2025")
        pom.url.set("https://github.com/block/artifact-swap/")
        pom.licenses { licenses ->
          licenses.license { license ->
            license.name.set("Apache 2.0")
            license.url.set("https://www.apache.org/licenses/LICENSE-2.0")
            license.distribution.set("https://www.apache.org/licenses/LICENSE-2.0")
          }
        }
        pom.developers { developers ->
          developers.developer { developer ->
            developer.id.set("block")
            developer.name.set("Block Open Source")
            developer.email.set("opensource@block.xyz")
          }
        }
        pom.scm { scm ->
          scm.url.set("https://github.com/block/artifact-swap/")
          scm.connection.set("scm:git:git://github.com/block/artifact-swap.git")
          scm.developerConnection.set("scm:git:ssh://git@github.com/block/artifact-swap.git")
        }
      }
    }
  }
}