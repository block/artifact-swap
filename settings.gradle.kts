pluginManagement {
  repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
  }

  includeBuild("build-logic")

  plugins {
    id("conventions.settings")
    id("com.google.devtools.ksp") version "2.2.20-2.0.4"
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
    id("com.gradleup.shadow") version "9.2.2"
  }
}

plugins {
  id("conventions.settings")
  // Use the Foojay Toolchains plugin to automatically download JDKs required by subprojects.
  id("org.gradle.toolchains.foojay-resolver-convention")
}

include(":cli")
include(":core")
include(":gradle-plugin")

rootProject.name = "artifactswap"
