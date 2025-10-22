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
  testRuntimeOnly(libs.kotlin.test)
}