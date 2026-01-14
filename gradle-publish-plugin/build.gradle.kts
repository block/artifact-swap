plugins {
  id("java-gradle-plugin")
}

gradlePlugin {
  vcsUrl = "https://github.com/block/artifact-swap"
  website = "https://github.com/block/artifact-swap"
  plugins {
    create("artifactSwapProjectPublishPlugin") {
      id = "xyz.block.artifactswap.publish"
      implementationClass = "xyz.block.artifactswap.ArtifactSwapProjectPublishPlugin"
    }
  }
}

kotlin {
  explicitApi()
}

dependencies {
  implementation(project(":core"))
  implementation(project(":gradle-utils"))
  implementation(gradleApi())

  compileOnly(libs.android.gradle.api)

  lintChecks(libs.androidx.lintGradle)
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
