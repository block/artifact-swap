plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    id("java-gradle-plugin")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}

gradlePlugin {
    plugins {
        create("artifactSwapPlugin") {
            id = "xyz.block.artifactswap"
            implementationClass = "xyz.block.artifactswap.gradle.ArtifactSwapSettingsPlugin"
        }
    }
}