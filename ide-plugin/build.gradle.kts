import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  kotlin("jvm")
  id("org.jetbrains.intellij.platform")
}

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

configurations.all {
  resolutionStrategy {
    // Force OkHttp 4.x for IntelliJ Platform Gradle Plugin compatibility
    // The plugin's publishPlugin task uses OkHttp internally and is not compatible with OkHttp 5.x
    // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/530
    force("com.squareup.okhttp3:okhttp:4.12.0")
  }
}


kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_21)
  }
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

// Otter 3 stable
val androidStudioTarget = "2025.2.3.9"
// Android studio Otter equivalent
// https://plugins.jetbrains.com/docs/intellij/android-studio-releases-list.html
val intellijTarget = "2025.2.1"

dependencies {
  api(project(":core"))

  implementation(libs.jetbrains.annotations)

  intellijPlatform {
    // Use Android Studio as base IDE to get Android plugin bundled
    androidStudio(androidStudioTarget)

    bundledPlugin("com.intellij.gradle")
    bundledPlugin("com.intellij.java")
    bundledPlugin("org.jetbrains.kotlin")
    
    // Android plugin is bundled with Android Studio
    bundledPlugin("org.jetbrains.android")

    // Artifact Swap builds on top of Spotlight
    plugin("com.fueledbycaffeine.spotlight:${libs.versions.spotlight.get()}")

    intellijPlatformTesting.runIde.register("runLocalIde") {
      // https://plugins.jetbrains.com/docs/intellij/android-studio.html#configuring-the-plugin-gradle-build-script
      val path = providers.gradleProperty("intellijPlatformTesting.idePath").orNull
      if (path != null) {
        localPath.set(file(path))
      }
    }
  }
}

val projectVersion: String = providers.gradleProperty("version").get()

fun isSnapshot(): Boolean = projectVersion.endsWith("SNAPSHOT")

version = if (isSnapshot()) {
  "$projectVersion-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))}"
} else {
  projectVersion
}

// https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
  pluginConfiguration {
    name = "Artifact Swap"
    description = """
      Manage large Gradle builds in your IDE by swapping projects with prebuild artifacts (https://engineering.block.xyz/blog/shrinking-elephants)
    """.trimIndent()

    ideaVersion {
      // Android studio Otter 1 equivalent
      // https://plugins.jetbrains.com/docs/intellij/android-studio-releases-list.html
      sinceBuild = "251"
    }
  }

  publishing {
    token = providers.environmentVariable("JETBRAINS_MARKETPLACE_SQUARE_PLUGINS")
      .orElse(providers.gradleProperty("jetbrainsMarketplaceToken"))
    channels = if (isSnapshot()) listOf("EAP") else listOf("Stable")
  }

  pluginVerification {
    ides {
      create(IntelliJPlatformType.AndroidStudio, androidStudioTarget) {}
      // Also verify against IntelliJ IDEA (where Android plugin won't be available)
      create(IntelliJPlatformType.IntellijIdea, intellijTarget) {}
    }

    // Unresolved Android plugin classes are expected when verifying against IntelliJ IDEA
    // These are from the optional Android plugin dependency and are handled at runtime
    // See ignored-problems.txt for the list of ignored patterns
  }
}
