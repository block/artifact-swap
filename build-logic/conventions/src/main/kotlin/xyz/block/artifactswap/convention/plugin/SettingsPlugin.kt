@file:Suppress("UnstableApiUsage")

package xyz.block.artifactswap.convention.plugin

import com.autonomousapps.BuildHealthPlugin
import com.autonomousapps.DependencyAnalysisExtension
import com.gradle.develocity.agent.gradle.DevelocityConfiguration
import com.gradle.develocity.agent.gradle.DevelocityPlugin
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.resolve.RepositoriesMode
import java.net.URI

@Suppress("unused")
class SettingsPlugin : Plugin<Settings> {
  override fun apply(target: Settings): Unit = target.run {
    configureDependencyManagement()
    configureDevelocity()
    configureDependencyAnalysis()

    // Apply conventions to projects
    gradle.lifecycle.beforeProject { project ->
      project.plugins.apply(ChecksPlugin::class.java)

      // IDE plugin has different target jvm requirements and a separate publishing plugin
      if (!project.path.startsWith(":ide-plugin")) {
        project.plugins.apply(BasePlugin::class.java)
        project.plugins.apply(PublishPlugin::class.java)
      }
    }
  }

  private fun Settings.configureDevelocity() {
    plugins.apply(DevelocityPlugin::class.java)
    extensions.getByType(DevelocityConfiguration::class.java).run {
      buildScan { scan ->
        scan.publishing.onlyIf { true }
        scan.termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        scan.termsOfUseAgree.set("yes")

        if (System.getenv("CI") != null) {
          scan.tag("CI")
        } else {
          scan.tag("Local")
        }
      }
    }
  }

  private fun Settings.configureDependencyManagement() {
    pluginManagement { pm ->
      pm.repositories { repos ->
        repos.mavenCentral()
        repos.google()
        repos.gradlePluginPortal()
      }
    }

    dependencyResolutionManagement { drm ->
      drm.repositories { repos ->
        repos.mavenCentral()
        repos.google()
        repos.gradlePluginPortal()

        // Gradle tooling API
        // https://docs.gradle.org/current/userguide/tooling_api.html#sec:embedding_quickstart
        val tapiRepo = repos.maven { m ->
          m.url = URI("https://repo.gradle.org/gradle/libs-releases")
        }
        repos.exclusiveContent { ex ->
          ex.forRepositories(tapiRepo)
          ex.filter { config ->
            config.includeModule("org.gradle", "gradle-tooling-api")
          }
        }
      }
    }
  }

  private fun Settings.configureDependencyAnalysis() {
    plugins.apply(BuildHealthPlugin::class.java)
    extensions.getByType(DependencyAnalysisExtension::class.java).run {
      reporting { reporting ->
        reporting.printBuildHealth(true)
      }
      issues { issues ->
        issues.all { all ->
          all.onAny { issue ->
            issue.severity("fail")
          }
          all.onDuplicateClassWarnings { issue ->
            // Provided by both IntelliJ bundled kotlin plugin and org.jetbrains:annotations
            issue.exclude("org/jetbrains/annotations/NotNull")
          }
        }
      }
    }
  }
}
