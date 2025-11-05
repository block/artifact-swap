package xyz.block.artifactswap.convention.plugin

import com.android.build.gradle.LintPlugin
import com.ncorti.ktfmt.gradle.KtfmtExtension
import com.ncorti.ktfmt.gradle.KtfmtPlugin
import com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask
import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.plugin.devel.tasks.ValidatePlugins

@Suppress("unused")
class ChecksPlugin : Plugin<Project> {
  override fun apply(target: Project): Unit = target.run {
    // Formatting
    plugins.apply(KtfmtPlugin::class.java)
    extensions.getByType(KtfmtExtension::class.java).run {
      googleStyle()
    }
    tasks.withType(KtfmtBaseTask::class.java).configureEach { task ->
      // Skip .kts files
      task.exclude("**/*.kts")
    }
    tasks.register("ktfmt") { it.dependsOn("ktfmtFormat") }

    tasks.withType(Test::class.java).configureEach { task ->
      // Configure all test Gradle tasks to use JUnitPlatform.
      task.useJUnitPlatform()

      // Log information about all test results, not only the failed ones.
      task.testLogging { logging ->
        logging.events(
          TestLogEvent.FAILED,
          TestLogEvent.PASSED,
          TestLogEvent.SKIPPED
        )
      }
    }

    // For plugin projects
    tasks.withType(ValidatePlugins::class.java).configureEach {
      it.enableStricterValidation.set(true)
    }

    // Gradle linting
    pluginManager.apply(LintPlugin::class.java)
  }
}