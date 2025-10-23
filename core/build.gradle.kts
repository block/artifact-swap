plugins {
  id("org.jetbrains.kotlin.jvm")
}

group = "xyz.block.artifactswap"
version = "0.1.0-SNAPSHOT"

dependencies {
  implementation(libs.jackson.dataformat.xml)
  implementation(libs.jackson.module.kotlin)
  implementation(libs.kotlinxCoroutines)
  implementation(libs.okhttp)
  implementation(libs.retrofit.core)
  implementation(libs.retrofit.converter.jackson)

  testImplementation(platform(libs.junit.bom))
  testRuntimeOnly(libs.junit.launcher)
}
