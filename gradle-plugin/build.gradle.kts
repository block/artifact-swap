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

dependencies {
  implementation(gradleApi())
  implementation(libs.kotlin.utilio)

  compileOnly(libs.android.gradle.api)

  lintChecks(libs.androidx.lintGradle)
}
