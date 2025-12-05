plugins {
  id("com.google.devtools.ksp")
  `java-test-fixtures`
}

dependencies {
  // API - exposed to consumers
  api(libs.gradle.tooling.api)
  api(libs.jackson.databind)
  api(libs.jackson.dataformat.xml)
  api(libs.jgit.core)
  api(libs.moshi)
  api(libs.retrofit.core)

  // Implementation
  implementation(libs.jackson.core)
  implementation(libs.jackson.module.kotlin)
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
  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockito.kotlin)
  testImplementation(testFixtures(project(":core")))
  testRuntimeOnly(libs.junit.launcher)

  lintChecks(libs.androidx.lintGradle)

  // Test fixtures dependencies
  testFixturesApi(libs.retrofit.core)
  testFixturesImplementation(libs.kotlinxCoroutines)
  testFixturesImplementation(libs.okhttp)
  testFixturesImplementation(libs.okio)
}
