plugins {
    application
    id("com.gradleup.shadow")
}

application {
    mainClass = "xyz.block.artifactswap.cli.MainKt"
    applicationName = "artifactswap"
}

dependencies {
  implementation(project(":core"))
  implementation(libs.bundles.log4j)
  implementation(libs.gradle.tooling.api)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.dataformat.xml)
  implementation(libs.jackson.module.kotlin)
  implementation(libs.koin.core)
  implementation(libs.kotlinxCoroutines)
  implementation(libs.moshi)
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging)
  implementation(libs.picocli.core)
  implementation(libs.retrofit.core)
  implementation(libs.retrofit.converter.jackson)
  implementation(libs.retrofit.wire)
  implementation(libs.slf4j.api)

  runtimeOnly(libs.log4j.slf4j2.impl) {
    because("JGit uses SLF4J for logging")
  }

  // Test dependencies
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockito.kotlin)
  testImplementation(testFixtures(project(":core")))
  testRuntimeOnly(libs.junit.launcher)
}
