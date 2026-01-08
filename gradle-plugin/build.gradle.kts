plugins {
  id("java-gradle-plugin")
  id("groovy")
}

gradlePlugin {
  vcsUrl = "https://github.com/block/artifact-swap"
  website = "https://github.com/block/artifact-swap"
  plugins {
    create("artifactSwapSettingsPlugin") {
      id = "xyz.block.artifactswap.settings"
      implementationClass = "xyz.block.artifactswap.ArtifactSwapSettingsPlugin"
    }
    create("artifactSwapProjectPlugin") {
      id = "xyz.block.artifactswap"
      implementationClass = "xyz.block.artifactswap.ArtifactSwapProjectPlugin"
    }
    create("artifactSwapProjectPublishPlugin") {
      id = "xyz.block.artifactswap.publish"
      implementationClass = "xyz.block.artifactswap.ArtifactSwapProjectPublishPlugin"
    }
    create("groovyProjectOverridePlugin") {
      id = "xyz.block.artifactswap.groovy-override"
      implementationClass = "xyz.block.artifactswap.ArtifactSwapGroovyProjectOverridePlugin"
    }
  }
}

kotlin {
  explicitApi()
}

dependencies {
  implementation(project(":core"))
  implementation(gradleApi())
  implementation(libs.jackson.databind)
  implementation(libs.jackson.dataformat.xml)
  implementation(libs.jackson.module.kotlin)
  implementation(libs.kotlin.utilio)
  implementation(libs.kotlinxCoroutines)
  implementation(libs.moshi)
  implementation(libs.okhttp)
  implementation(libs.retrofit.converter.jackson)
  implementation(libs.retrofit.core)
  implementation(libs.slf4j.api)
  implementation(libs.spotlight.buildscriptUtils)
  implementation(libs.spotlight.gradle)

  compileOnly(libs.android.gradle.api)

  runtimeOnly(libs.jackson.core)
  runtimeOnly(libs.jackson.module.kotlin)

  lintChecks(libs.androidx.lintGradle)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter.api)
  testRuntimeOnly(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.launcher)
}

listOf("runtimeElements", "apiElements").forEach { configurationName ->
  configurations.named(configurationName).configure {
    attributes {
      attribute(
        GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE,
        objects.named(libs.versions.minGradle.get())
      )
    }
  }
}