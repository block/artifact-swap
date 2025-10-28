package xyz.block.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import java.io.File

private const val SQUARE_GENERATED_PROTOS_VERSION = "square.protosGeneratedVersion"
private const val SQUARE_PROTOS_SCHEMA_VERSION = "square.protosSchemaVersion"
const val USE_ARTIFACT_SYNC = "useArtifactSync"
internal const val LOCAL_PROTOS_ARTIFACTS = "square.useLocalProtos"
private const val IS_SANDBAG_PUBLISHING = "square.enableSandbagPublishing"
private const val SANDBAG_HASH_FILE = "square.hashFile"

internal val Project.generatedProtosVersion
  get() = providers.gradleProperty(SQUARE_GENERATED_PROTOS_VERSION).get()

internal val Project.protosSchemaVersion
  get() = providers.gradleProperty(SQUARE_PROTOS_SCHEMA_VERSION).get()

/**
 * Indicates if Artifact Sync should be enabled
 */
val Project.useArtifactSync: Boolean
  get() = providers.gradleProperty(USE_ARTIFACT_SYNC)
    .getOrElse("false").toBoolean()

val Settings.useArtifactSync: Boolean
  get() = providers.gradleProperty(USE_ARTIFACT_SYNC)
    .getOrElse("false").toBoolean()

val Settings.bomVersion: String
  get() = extensions.extraProperties.properties["sandbagVersion"]!!.toString()

internal val Settings.useLocalProtos: Boolean
  get() = providers.gradleProperty(LOCAL_PROTOS_ARTIFACTS)
    .getOrElse("false").toBoolean()

val Project.isSandbagPublishingEnabled: Boolean
  get() = providers.gradleProperty(IS_SANDBAG_PUBLISHING)
    .getOrElse("false").toBoolean()

val Settings.isSandbagPublishingEnabled: Boolean
  get() = providers.gradleProperty(IS_SANDBAG_PUBLISHING)
    .getOrElse("false").toBoolean()

/**
 * Gets the sandbag version for this project from the sandbag hash file.
 */
val Project.sandbagVersion: String
  get() {
    val sandbagHashFile = File(rootDir, providers.gradleProperty(SANDBAG_HASH_FILE).get())
    if (!sandbagHashFile.exists()) {
      throw GradleException(
        "Sandbag hash file was not found in $sandbagHashFile. Please run sandbag tool."
      )
    }
    val result = sandbagHashFile.useLines { lines ->
      return@useLines lines.firstOrNull { line ->
        return@firstOrNull line.substringBefore('|') == project.path
      } ?: throw GradleException("$path not found in hashing file. Please re-run sandbag tool.")
    }
    return result.substringAfter('|')
  }
