plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.android.lint")
}

dependencies {
  implementation(gradleApi())

  lintChecks(libs.androidx.lintGradle)
}
