plugins {
  id("org.jetbrains.kotlin.jvm")
  id("java-gradle-plugin")
}

gradlePlugin {
  plugins {
    create("conventions.settings") {
      id = "conventions.settings"
      implementationClass = "xyz.block.artifactswap.convention.plugin.SettingsPlugin"
    }
    create("conventions.checks") {
      id = "conventions.checks"
      implementationClass = "xyz.block.artifactswap.convention.plugin.ChecksPlugin"
    }
    create("conventions.base") {
      id = "conventions.base"
      implementationClass = "xyz.block.artifactswap.convention.plugin.BasePlugin"
    }
    create("conventions.publish") {
      id = "conventions.publish"
      implementationClass = "xyz.block.artifactswap.convention.plugin.PublishPlugin"
    }
  }
}

dependencies {
  implementation(gradleApi())
  implementation(libs.android.gradle)
  implementation(libs.dagp.gradle)
  implementation(libs.develocity.gradle)
  implementation(libs.kotlin.gradle)
  implementation(libs.ktfmt.gradle)
  implementation(libs.vanniktech.gradle)
}