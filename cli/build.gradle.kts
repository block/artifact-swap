plugins {
    application
    id("com.gradleup.shadow")
    id("conventions.publish")
}

application {
    mainClass = "xyz.block.artifactswap.cli.MainKt"
    applicationName = "artifactswap"
}

dependencies {
  implementation(project(":core"))
  implementation(libs.bundles.log4j)
  implementation(libs.gradle.tooling.api)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.dataformat.xml)
  implementation(libs.jackson.module.kotlin)
  implementation(libs.koin.core)
  implementation(libs.kotlinxCoroutines)
  implementation(libs.moshi)
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging)
  implementation(libs.picocli.core)
  implementation(libs.retrofit.core)
  implementation(libs.retrofit.converter.jackson)
  implementation(libs.retrofit.wire)
  implementation(libs.slf4j.api)

  runtimeOnly(libs.log4j.slf4j2.impl) {
    because("JGit uses SLF4J for logging")
  }

  // Test dependencies
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockito.kotlin)
  testImplementation(testFixtures(project(":core")))
  testRuntimeOnly(libs.junit.launcher)
}

val shadowJar = tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
  // We set the Shadow Jar to have NO classifier, making it the "Main" artifact
  archiveClassifier.set("")
  mergeServiceFiles()
  minimize {
    exclude(dependency("org.slf4j:.*:.*"))
  }
  exclude("**/*.kotlin_metadata")
  exclude("**/*.kotlin_module")
  exclude("META-INF/maven/**")

  manifest {
    attributes(
      "Implementation-Title" to "Artifact Swap CLI",
      "Implementation-Version" to project.version,
      "Main-Class" to application.mainClass.get()
    )
  }
}

tasks.withType<CreateStartScripts>().configureEach {
  dependsOn(shadowJar)
}

// GMM is not useful in this case and trying to generate it throws errors
// because the publication component was changed to a zip file.
tasks.withType<GenerateModuleMetadata>().configureEach {
  enabled = false
}

// publish cli as a zip of the shadow jar outputs
publishing {
  publications.withType<MavenPublication>().configureEach {
    artifacts.clear()
    artifact(tasks.named("shadowDistZip")) {
      classifier = null
      extension = "zip"
    }
  }
}