plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.android.lint")
  id("com.vanniktech.maven.publish")
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

mavenPublishing {
  publishToMavenCentral()
  signAllPublications()

  pom {
    name = "Artifact Swap Gradle Plugin"
    description = "A plugin that helps manage large Gradle builds"
    inceptionYear = "2025"
    url = "https://github.com/block/artifact-swap/"
    licenses {
      license {
        name = "Apache 2.0"
        url = "https://www.apache.org/licenses/LICENSE-2.0"
        distribution = "https://www.apache.org/licenses/LICENSE-2.0"
      }
    }
    scm {
      url = "https://github.com/block/artifact-swap/"
      connection = "scm:git:git://github.com/block/artifact-swap.git"
      developerConnection = "scm:git:ssh://git@github.com/block/artifact-swap.git"
    }
  }
}

dependencies {
  implementation(gradleApi())
  implementation(libs.kotlin.utilio)

  lintChecks(libs.androidx.lintGradle)
}

tasks.withType(ValidatePlugins::class.java).configureEach {
  enableStricterValidation = true
}