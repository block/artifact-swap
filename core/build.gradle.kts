plugins {
  id("org.jetbrains.kotlin.jvm")
}

group = "xyz.block.artifactswap"
version = "0.1.0-SNAPSHOT"

dependencies {
  testImplementation(platform(libs.junit.bom))
  testRuntimeOnly(libs.junit.launcher)
}
