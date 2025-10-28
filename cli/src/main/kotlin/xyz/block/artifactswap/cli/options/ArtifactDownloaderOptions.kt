package xyz.block.artifactswap.cli.options

import picocli.CommandLine.Option
import java.nio.file.Path
import kotlin.io.path.Path

class ArtifactDownloaderOptions(
    bomVersion: String = "",
    gradlePropertiesFile: Path = Path("gradle.properties"),
    settingsGradleFile: Path = Path("settings.gradle.kts"),
    mavenLocalPath: Path = Path(System.getProperty("user.home")).resolve(".m2/repository"),
) {

    @Option(
        names = ["--bom-version"],
        description = ["BOM version to check artifactory for artifacts"]
    )
    var bomVersion: String = bomVersion
        internal set

    @Option(
        names = ["--gradle-properties-file"],
        description = ["path to gradle.properties to extract protos version from"]
    )
    var gradlePropertiesFile: Path = gradlePropertiesFile
        internal set

    @Option(
        names = ["--settings-gradle-file"],
        description = ["path to settings.gradle to extract protos projects from"]
    )
    var settingsGradleFile: Path = settingsGradleFile
        internal set

    @Option(
        names = ["--maven-local-path"],
        description = ["Local path to store downloaded artifacts"]
    )
    var mavenLocalPath: Path = mavenLocalPath
        internal set
}
