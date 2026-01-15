package xyz.block.artifactswap

import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.initialization.Settings

private const val IS_ARTIFACT_SWAP_PUBLISHING_ENABLED = "artifactswap.publishingEnabled"
private const val ARTIFACT_VERSION_FILE = "artifactswap.artifactVersionFile"

internal val Settings.isArtifactPublishingEnabled: Boolean
  get() =
    providers.gradleProperty(IS_ARTIFACT_SWAP_PUBLISHING_ENABLED).getOrElse("false").toBoolean()

internal val Project.projectVersionsFile: File
  get() = File(rootDir, providers.gradleProperty(ARTIFACT_VERSION_FILE).get())

/** Gets the project artifact version for this project from the project versions file. */
internal val Project.projectArtifactVersion: String?
  get() {
    if (!projectVersionsFile.exists()) {
      throw GradleException(
        "project versions file was not found in $projectVersionsFile. Please run artifactswap tool."
      )
    }
    val result =
      projectVersionsFile.useLines { lines ->
        return@useLines lines.firstOrNull { line ->
          return@firstOrNull line.substringBefore('|') == project.path
        }
      }
    return result?.substringAfter('|')
  }
