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
    id("com.gradleup.shadow") version "9.3.1"
    id("org.jetbrains.intellij.platform") version "2.11.0"
  }
}

// WORKAROUND: Force OkHttp 5.x in buildscript classpath for IntelliJ Platform plugin tasks
//
// plugin-repository-rest-client's POM excludes okhttp from its direct retrofit dependency but
// not from retrofit-converter-jaxb/jackson, which transitively bring in okhttp:3.14.9. The
// binary was compiled against OkHttp 4.x+ Companion APIs. OkHttp 3.x (Java) has no Companion
// objects, while 4.x+ (Kotlin) does. Without forcing, Gradle may resolve 3.x, causing
// NoSuchFieldError when publishPlugin accesses Companion fields that don't exist in 3.x.
//
// Must be combined with forcing OkHttp in ide-plugin/build.gradle.kts configurations.
//
// See: https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/530
buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath("com.squareup.okhttp3:okhttp:5.3.2")
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
include(":gradle-publish-plugin")
include(":gradle-tooling")
include(":gradle-utils")
include(":ide-plugin")

rootProject.name = "artifactswap"
