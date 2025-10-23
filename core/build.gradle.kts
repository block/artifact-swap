import org.gradle.api.tasks.testing.logging.TestLogEvent

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
  implementation(libs.jgit.core)
  implementation(libs.koin.core)
  implementation(libs.kotlinxCoroutines)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.okio)
  implementation(libs.retrofit.core)
  implementation(libs.retrofit.converter.jackson)
  implementation(libs.retrofit.wire)
  implementation(libs.wire.runtime)

  ksp(libs.moshi.kotlin.codegen)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotlinx.coroutines.test)
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
