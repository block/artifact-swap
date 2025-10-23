plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.google.devtools.ksp")
}

group = "xyz.block.artifactswap"
version = "0.1.0-SNAPSHOT"

dependencies {
  implementation(libs.bundles.log4j)
  implementation(libs.gradle.tooling.api)
  implementation(libs.jackson.dataformat.xml)
  implementation(libs.jackson.module.kotlin)
  implementation(libs.kotlinxCoroutines)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit.core)
  implementation(libs.retrofit.converter.jackson)
  implementation(libs.retrofit.wire)
  implementation(libs.wire.runtime)

  ksp(libs.moshi.kotlin.codegen)

  testImplementation(platform(libs.junit.bom))
  testRuntimeOnly(libs.junit.launcher)
}
