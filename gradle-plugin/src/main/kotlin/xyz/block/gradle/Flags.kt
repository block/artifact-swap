package xyz.block.gradle

import org.gradle.api.Project
import org.gradle.api.initialization.Settings

private const val SQUARE_GENERATED_PROTOS_VERSION = "square.protosGeneratedVersion"
private const val SQUARE_PROTOS_SCHEMA_VERSION = "square.protosSchemaVersion"
const val USE_ARTIFACT_SYNC = "useArtifactSync"
internal const val LOCAL_PROTOS_ARTIFACTS = "square.useLocalProtos"

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
