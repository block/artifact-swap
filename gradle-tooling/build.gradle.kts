plugins {
  id("com.autonomousapps.testkit")
}

dependencies {
  api(project(":core"))
  api(libs.gradle.tooling.api)

  implementation(libs.koin.core)
  implementation(libs.kotlinxCoroutines)
  implementation(libs.log4j.kotlin)

  testImplementation(platform(libs.junit.bom))
  testImplementation(testFixtures(project(":core")))
  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotlinx.coroutines.test)

  testRuntimeOnly(libs.junit.launcher)

  lintChecks(libs.androidx.lintGradle)
}
