plugins {
  id("java-gradle-plugin")
  id("groovy")
  id("com.autonomousapps.testkit")
}

kotlin {
  explicitApi()
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
    create("groovyProjectOverridePlugin") {
      id = "xyz.block.artifactswap.groovy-override"
      implementationClass = "xyz.block.artifactswap.ArtifactSwapGroovyProjectOverridePlugin"
    }
  }
}

tasks.named<Test>("functionalTest") {
  useJUnitPlatform()
  
  testLogging {
    events("passed", "skipped", "failed")
    showStandardStreams = true
    showExceptions = true
  }
}

gradleTestKitSupport {
  // Dependencies are declared explicitly in the dependencies block below
  // to allow buildHealth to manage their configurations (api vs implementation vs runtimeOnly)
}

dependencies {
  api(project(":core"))

  implementation(project(":gradle-utils"))
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

  compileOnly(project(":gradle-publish-plugin"))
  compileOnly(libs.android.gradle.api)
  compileOnly(libs.develocity.gradle)
  compileOnly(libs.spotlight.gradle)

  runtimeOnly(libs.jackson.core)
  runtimeOnly(libs.jackson.module.kotlin)

  lintChecks(libs.androidx.lintGradle)

  functionalTestImplementation(project(":cli"))
  functionalTestImplementation(project(":core"))
  functionalTestImplementation(libs.picocli.core)
  functionalTestImplementation(platform(libs.junit.bom))
  functionalTestApi(libs.autonomousapps.testkit.gradle)
  functionalTestApi(libs.junit.jupiter.api)
  functionalTestImplementation(libs.autonomousapps.testkit.truth)
  functionalTestImplementation(libs.spotlight.buildscriptUtils)
  functionalTestImplementation(libs.truth)
  functionalTestRuntimeOnly(libs.jackson.core)
  functionalTestRuntimeOnly(libs.jackson.databind)
  functionalTestRuntimeOnly(libs.jackson.dataformat.xml)
  functionalTestRuntimeOnly(libs.kotlin.test.junit5)
  functionalTestRuntimeOnly(libs.woodstox.core)
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
