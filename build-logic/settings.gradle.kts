@file:Suppress("UnstableApiUsage")

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
  }
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

  versionCatalogs {
    create("libs") {
      from(files("../gradle/libs.versions.toml"))
    }
  }
}

pluginManagement {
  repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
  }
  plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
  }
}

rootProject.name = "build-logic"

include(":conventions")