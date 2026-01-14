
kotlin {
  explicitApi()
}

dependencies {
  api(project(":core"))

  compileOnly(gradleApi())

  lintChecks(libs.androidx.lintGradle)
}
