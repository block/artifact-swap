import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.google.devtools.ksp")
}

group = "xyz.block.artifactswap"
version = "0.1.0-SNAPSHOT"

dependencies {
  // API - exposed to consumers
  api(libs.jackson.databind)
  api(libs.jackson.dataformat.xml)
  api(libs.jgit.core)
  api(libs.moshi)
  api(libs.retrofit.core)

  // Implementation
  implementation(libs.gradle.tooling.api)
  implementation(libs.jackson.core)
  implementation(libs.jackson.module.kotlin)
  implementation(libs.koin.core)
  implementation(libs.kotlinxCoroutines)
  implementation(libs.log4j.kotlin)
  implementation(libs.okhttp)
  implementation(libs.okio)
  implementation(libs.slf4j.api)
  implementation(libs.wire.runtime)

  // Runtime only
  runtimeOnly(libs.log4j.api)
  runtimeOnly(libs.log4j.core)

  ksp(libs.moshi.kotlin.codegen)

  // Test dependencies
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.gradle.tooling.api)
  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockito.kotlin)
  testRuntimeOnly(libs.junit.launcher)
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
