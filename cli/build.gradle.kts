plugins {
    id("org.jetbrains.kotlin.jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application
}

group = "xyz.block.artifactswap"
version = "0.1.0-SNAPSHOT"

application {
    mainClass = "xyz.block.artifactswap.cli.MainKt"
}

dependencies {
  implementation(project(":core"))
  implementation(libs.bundles.log4j)
  implementation(libs.gradle.tooling.api)
  implementation(libs.jackson.dataformat.xml)
  implementation(libs.jackson.module.kotlin)
  implementation(libs.koin.core)
  implementation(libs.kotlinxCoroutines)
  implementation(libs.logback.classic)
  implementation(libs.logback.core)
  implementation(libs.okhttp)
  implementation(libs.picocli.core)
  implementation(libs.retrofit.core)
  implementation(libs.retrofit.converter.jackson)
  implementation(libs.retrofit.wire)

  testRuntimeOnly(libs.kotlin.test)
}