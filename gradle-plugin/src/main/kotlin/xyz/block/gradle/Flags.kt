package xyz.block.gradle

import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import xyz.block.artifactswap.ArtifactSwapProjectPlugin

private const val SQUARE_GENERATED_PROTOS_VERSION = "square.protosGeneratedVersion"
private const val SQUARE_PROTOS_SCHEMA_VERSION = "square.protosSchemaVersion"
internal const val LOCAL_PROTOS_ARTIFACTS = "square.useLocalProtos"
public const val ARTIFACT_SWAP_ENABLED: String = "artifactswap.enabled"
private const val IS_ARTIFACT_SWAP_PUBLISHING_ENABLED = "artifactswap.publishingEnabled"
private const val ARTIFACT_VERSION_FILE = "artifactswap.artifactVersionFile"
internal val Settings.useLocalProtos: Boolean
  get() = providers.gradleProperty(LOCAL_PROTOS_ARTIFACTS).getOrElse("false").toBoolean()

internal val Project.generatedProtosVersion
  get() = providers.gradleProperty(SQUARE_GENERATED_PROTOS_VERSION).get()

internal val Project.protosSchemaVersion
  get() = providers.gradleProperty(SQUARE_PROTOS_SCHEMA_VERSION).get()

/** Indicates if Artifact Sync should be enabled */
public val Settings.useArtifactSwap: Boolean
  get() = providers.gradleProperty(ARTIFACT_SWAP_ENABLED).getOrElse("true").toBoolean()

/** Indicates if artifact swap is currently active */
public val Project.isArtifactSwapActive: Boolean
  get() = pluginManager.hasPlugin(ArtifactSwapProjectPlugin.ID)

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
