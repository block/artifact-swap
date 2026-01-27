plugins {
  application
  id("com.gradleup.shadow")
}

application {
  mainClass = "xyz.block.artifactswap.cli.MainKt"
  applicationName = "artifactswap"
}

dependencies {
  implementation(project(":core"))
  implementation(project(":gradle-tooling"))
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
  implementation(libs.retrofit.converter.jackson)
  implementation(libs.retrofit.core)
  implementation(libs.retrofit.wire)
  implementation(libs.slf4j.api)

  runtimeOnly(libs.log4j.slf4j2.impl) {
    because("JGit uses SLF4J for logging")
  }

  testImplementation(platform(libs.junit.bom))
  testImplementation(testFixtures(project(":core")))
  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockito.kotlin)

  testRuntimeOnly(libs.junit.launcher)
}

val shadowJar = tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
  mergeServiceFiles()
  minimize {
    exclude(dependency("org.slf4j:.*:.*"))

    exclude(dependency("org.apache.logging.log4j:.*:.*"))

    exclude(dependency("com.fasterxml.jackson.*:.*:.*"))
    exclude(dependency("org.codehaus.woodstox:.*:.*"))
    exclude(dependency("com.fasterxml.woodstox:.*:.*"))
    exclude(project(":core"))
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

tasks.named<Zip>("shadowDistZip") {
  archiveBaseName = "artifactswap"
}

tasks.named<Tar>("shadowDistTar") {
  archiveBaseName = "artifactswap"
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
