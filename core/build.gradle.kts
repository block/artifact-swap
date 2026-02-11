plugins {
  id("com.google.devtools.ksp")
  id("com.autonomousapps.testkit")
  `java-test-fixtures`
}

dependencies {
  api(libs.jackson.databind)
  api(libs.jackson.dataformat.xml)
  api(libs.jgit.core)
  api(libs.kotlinxCoroutines)
  api(libs.moshi)
  api(libs.okhttp)
  api(libs.retrofit.core)
  api(libs.slf4j.api)
  api(libs.spotlight.buildscriptUtils)

  implementation(libs.jackson.core)
  implementation(libs.jackson.module.kotlin)
  implementation(libs.log4j.kotlin)
  implementation(libs.okio)
  implementation(libs.wire.runtime)

  runtimeOnly(libs.log4j.api)
  runtimeOnly(libs.log4j.core)

  testFixturesApi(libs.jgit.core)
  testFixturesApi(libs.retrofit.core)

  testFixturesImplementation(libs.kotlinxCoroutines)
  testFixturesImplementation(libs.okhttp)
  testFixturesImplementation(libs.okio)

  testImplementation(platform(libs.junit.bom))
  testImplementation(testFixtures(project(":core")))
  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockito.kotlin)

  testRuntimeOnly(libs.junit.launcher)

  ksp(libs.moshi.kotlin.codegen)

  lintChecks(libs.androidx.lintGradle)
}
