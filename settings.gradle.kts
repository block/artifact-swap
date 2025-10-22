dependencyResolutionManagement {
  // Use Maven Central as the default repository (where Gradle will download dependencies) in all subprojects.
  @Suppress("UnstableApiUsage")
  repositories {
    mavenCentral()
    google()
  }
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
}

pluginManagement {
  repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
  }
  plugins {
    id("com.gradle.develocity") version "4.2.2"
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
    id("com.android.lint") version "8.13.0"
    id("com.autonomousapps.build-health") version "3.2.0"
    id("com.vanniktech.maven.publish") version "0.34.0"
  }
}

plugins {
  id("com.gradle.develocity")
  // Use the Foojay Toolchains plugin to automatically download JDKs required by subprojects.
  id("org.gradle.toolchains.foojay-resolver-convention")
  id("com.autonomousapps.build-health")
  id("org.jetbrains.kotlin.jvm") apply false
}

develocity {
  buildScan {
    publishing.onlyIf { true }
    termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
    termsOfUseAgree.set("yes")

    if (System.getenv("CI") != null) {
      tag("CI")
    } else {
      tag("Local")
    }
  }
}

include(":cli")
include(":core")
include(":gradle-plugin")
include(":gradle-utilities")

rootProject.name = "artifactswap"
