package xyz.block.ide

import org.gradle.api.Project
import org.gradle.api.initialization.Settings

const val INTELLIJ_SYNC_ACTIVE_SYSTEM_PROPERTY = "idea.sync.active"
const val SQUARE_SETTINGS_OVERRIDE_FORCE_PROPERTY = "square.force.modules.override"

val Project.isIdeSync: Boolean get() =
  providers.systemProperty(INTELLIJ_SYNC_ACTIVE_SYSTEM_PROPERTY).orNull != null

val Settings.isIdeSync: Boolean get() =
  providers.systemProperty(INTELLIJ_SYNC_ACTIVE_SYSTEM_PROPERTY).orNull != null


val Settings.forceSettingsModulesOverride: Boolean get() =
  providers.gradleProperty(SQUARE_SETTINGS_OVERRIDE_FORCE_PROPERTY).orNull != null
