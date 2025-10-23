import org.gradle.api.tasks.testing.logging.TestLogEvent

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

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockito.kotlin)
  testRuntimeOnly(libs.junit.launcher)
  testRuntimeOnly(libs.kotlin.test)
}

tasks.withType<Test>().configureEach {
    // Configure all test Gradle tasks to use JUnitPlatform.
    useJUnitPlatform()

    // Log information about all test results, not only the failed ones.
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}
