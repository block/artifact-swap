plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.android.lint")
  id("java-gradle-plugin")
  id("groovy")
}

gradlePlugin {
  plugins {
    create("artifactSwapSettingsPlugin") {
      id = "xyz.block.artifactswap.settings"
      implementationClass = "xyz.block.artifactswap.ArtifactSwapSettingsPlugin"
    }
    create("artifactSwapProjectPlugin") {
      id = "xyz.block.artifactswap"
      implementationClass = "xyz.block.artifactswap.ArtifactSwapProjectPlugin"
    }
    create("groovyProjectOverridePlugin") {
      id = "xyz.block.artifactswap.groovy-override"
      implementationClass = "xyz.block.artifactswap.ArtifactSwapGroovyProjectOverridePlugin"
    }
  }
}

dependencies {
  api(project(":gradle-utilities"))

  implementation(gradleApi())
  implementation(libs.kotlin.utilio)

  lintChecks(libs.androidx.lintGradle)
}

tasks.withType(ValidatePlugins::class.java).configureEach {
  enableStricterValidation = true
}