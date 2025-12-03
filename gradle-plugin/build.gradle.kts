plugins {
  id("conventions.publish")
  id("java-gradle-plugin")
  id("groovy")
}

gradlePlugin {
  vcsUrl = "https://github.com/block/artifact-swap"
  website = "https://github.com/block/artifact-swap"
  plugins {
    create("artifactSwapSettingsPluginOld") {
      id = "xyz.block.artifactswap.settings.old"
      implementationClass = "xyz.block.artifactswap.ArtifactSwapOldSettingsPlugin"
    }
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

dependencies {
  implementation(project(":core"))
  implementation(gradleApi())
  implementation(libs.gradle.tooling.api)
  implementation(libs.kotlin.utilio)
  implementation(libs.spotlight.buildscriptUtils)
  implementation(libs.spotlight.gradle)

  compileOnly(libs.android.gradle.api)

  runtimeOnly(libs.jackson.module.kotlin)

  lintChecks(libs.androidx.lintGradle)
}
