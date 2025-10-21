plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    id("java-gradle-plugin")
    id("groovy")
}

repositories {
    mavenCentral()
}

gradlePlugin {
  plugins {
    create("artifactSyncSettingsPlugin") {
      id = "com.squareup.artifactsync.settings"
      implementationClass = "com.squareup.register.artifactsync.ArtifactSyncSettingsPlugin"
    }
    create("artifactSyncProjectPlugin") {
      id = "com.squareup.artifactsync"
      implementationClass = "com.squareup.register.artifactsync.ArtifactSyncProjectPlugin"
    }
    create("groovyProjectOverridePlugin") {
      id = "com.squareup.artifactsync.groovy-override"
      implementationClass = "com.squareup.register.artifactsync.ArtifactSyncGroovyProjectOverridePlugin"
    }
    }
}

dependencies {
  implementation(project(":gradle-utilities"))
  implementation(gradleApi())
  implementation(libs.kotlin.utilio)
}
