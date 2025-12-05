dependencies {
  api(project(":core"))
  api(libs.gradle.tooling.api)

  implementation(libs.koin.core)
  implementation(libs.kotlinxCoroutines)
  implementation(libs.log4j.kotlin)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(testFixtures(project(":core")))
  testRuntimeOnly(libs.junit.launcher)

  lintChecks(libs.androidx.lintGradle)
}
