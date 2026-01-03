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
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.gradleup.shadow") version "9.3.0"
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
include(":gradle-tooling")

rootProject.name = "artifactswap"
