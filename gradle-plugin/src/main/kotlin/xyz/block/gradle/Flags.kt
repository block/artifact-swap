package xyz.block.gradle

import java.io.File
import org.gradle.api.Project
import org.gradle.api.initialization.Settings

private const val SQUARE_GENERATED_PROTOS_VERSION = "square.protosGeneratedVersion"
private const val SQUARE_PROTOS_SCHEMA_VERSION = "square.protosSchemaVersion"
internal const val LOCAL_PROTOS_ARTIFACTS = "square.useLocalProtos"
const val ARTIFACT_SWAP_ENABLED = "artifactswap.enabled"
private const val IS_ARTIFACT_SWAP_PUBLISHING_ENABLED = "artifactswap.publishingEnabled"
private const val ARTIFACT_VERSION_FILE = "artifactswap.artifactVersionFile"
const val ARTIFACT_SWAP_HASH_SEED = "artifactswap.hashSeed"
internal val Settings.useLocalProtos: Boolean
  get() = providers.gradleProperty(LOCAL_PROTOS_ARTIFACTS).getOrElse("false").toBoolean()

internal val Project.generatedProtosVersion
  get() = providers.gradleProperty(SQUARE_GENERATED_PROTOS_VERSION).get()

internal val Project.protosSchemaVersion
  get() = providers.gradleProperty(SQUARE_PROTOS_SCHEMA_VERSION).get()

/** Indicates if Artifact Sync should be enabled */
val Settings.useArtifactSwap: Boolean
  get() = providers.gradleProperty(ARTIFACT_SWAP_ENABLED).getOrElse("true").toBoolean()

internal val Settings.isArtifactPublishingEnabled: Boolean
  get() =
    providers.gradleProperty(IS_ARTIFACT_SWAP_PUBLISHING_ENABLED).getOrElse("false").toBoolean()

val Project.projectVersionsFile: File
  get() = File(rootDir, providers.gradleProperty(ARTIFACT_VERSION_FILE).get())

/**
 * Gets the project artifact version for this project from the project versions file.
 *
 * Returns null if:
 * - The project versions file doesn't exist
 * - The artifactVersionFile property is not set
 * - The project is not found in the versions file
 *
 * This allows the plugin to fall back to computing the version from sourcesets.
 */
val Project.projectArtifactVersion: String?
  get() {
    // Check if the property is configured
    val versionFilePath = providers.gradleProperty(ARTIFACT_VERSION_FILE).orNull ?: return null

    val versionFile = File(rootDir, versionFilePath)
    if (!versionFile.exists()) {
      return null
    }

    val result =
      versionFile.useLines { lines ->
        return@useLines lines.firstOrNull { line ->
          return@firstOrNull line.substringBefore('|') == project.path
        }
      }
    return result?.substringAfter('|')
  }
