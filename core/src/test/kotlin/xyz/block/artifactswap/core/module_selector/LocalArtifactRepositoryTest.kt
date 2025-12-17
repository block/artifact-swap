package xyz.block.artifactswap.core.module_selector

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.nio.file.Path
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xyz.block.artifactswap.core.config.testArtifactSwapConfig
import xyz.block.artifactswap.core.maven.Dependencies
import xyz.block.artifactswap.core.maven.Dependency
import xyz.block.artifactswap.core.maven.DependencyManagement
import xyz.block.artifactswap.core.maven.Project
import xyz.block.artifactswap.core.module_selector.ArtifactType.ANDROID_AAR
import xyz.block.artifactswap.core.module_selector.ArtifactType.POM
import xyz.block.artifactswap.core.module_selector.ArtifactType.SOURCES_JAR
import xyz.block.artifactswap.core.repository.InstalledArtifact
import xyz.block.artifactswap.core.repository.RealLocalArtifactRepository

class LocalArtifactRepositoryTest {

  @TempDir lateinit var fakeM2Root: Path
  private lateinit var artifactsDirectory: Path
  private val fakeBomVersion = "12345abcde"
  val fakeBom =
    Project(
      groupId = "com.squareup.register.sandbagging",
      artifactId = "hashing",
      version = fakeBomVersion,
      name = "hashing",
      dependencyManagement =
        DependencyManagement(dependencies = Dependencies(dependency = emptyList())),
    )
  val xmlMapper =
    XmlMapper.builder()
      .defaultUseWrapper(false)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .enable(SerializationFeature.INDENT_OUTPUT)
      .build()
      .registerKotlinModule()

  private val fakeArtifactDirectoryNames =
    listOf(
      "features_open-tickets-v2_create-ticket_impl-tablet",
      "features_open-tickets-v2_create-ticket_impl-tablet-robots",
      "features_open-tickets-v2_create-ticket_internal",
      "features_open-tickets-v2_create-ticket_public",
      "features_open-tickets-v2_home-ui_impl-mobile",
      "features_open-tickets-v2_home-ui_impl-mobile-robots",
      "features_open-tickets-v2_home-ui_impl-tablet",
      "features_open-tickets-v2_home-ui_impl-tablet-robots",
      "features_open-tickets-v2_home-ui_internal",
      "features_open-tickets-v2_home-ui_public",
      "features_open-tickets-v2_home_fake",
      "features_open-tickets-v2_home_impl",
    )
  private val testMavenGroup = "com.squareup.register.sandbags"
  private val config = testArtifactSwapConfig(primaryArtifactsMavenGroup = testMavenGroup)

  @BeforeEach
  fun setUp() {
    artifactsDirectory =
      fakeM2Root.resolve("com").resolve("squareup").resolve("register").resolve("sandbags")
    artifactsDirectory.createParentDirectories()
  }

  @Test
  fun `GIVEN bom not present WHEN getting installed artifacts THEN empty list returned`() =
    runTest {
      val localArtifactRepository =
        RealLocalArtifactRepository(
          xmlMapper = xmlMapper,
          ioContext = EmptyCoroutineContext,
          mavenDirectory = fakeM2Root,
          config = config,
        )
      val installedArtifacts = localArtifactRepository.getInstalledArtifacts(fakeBom)
      assertTrue(installedArtifacts.getOrThrow().isEmpty())
    }

  @Test
  fun `GIVEN bom present but no artifacts present from bom WHEN getting installed artifacts THEN empty list returned`() =
    runTest {
      val bom =
        populateBom(
          fakeM2Root,
          bomVersion = fakeBomVersion,
          dependentProjects =
            listOf(
              Dependency(
                groupId = testMavenGroup,
                artifactId = "features_open-tickets-v2_create-ticket_impl-tablet",
                version = "asdf1132",
              ),
              Dependency(
                groupId = testMavenGroup,
                artifactId = "features_open-tickets-v2_create-ticket_anvil-wiring",
                version = "a23kh1",
              ),
            ),
        )

      val localArtifactRepository =
        RealLocalArtifactRepository(
          xmlMapper = xmlMapper,
          ioContext = EmptyCoroutineContext,
          mavenDirectory = fakeM2Root,
          config = config,
        )
      val installedArtifacts = localArtifactRepository.getInstalledArtifacts(bom.getOrThrow())
      assertTrue(installedArtifacts.getOrThrow().isEmpty())
    }

  @Test
  fun `GIVEN bom present and artifacts present but not from bom version WHEN getting installed artifacts THEN empty list returned`() =
    runTest {
      val bom =
        populateBom(
          artifactsDirectory,
          bomVersion = fakeBomVersion,
          dependentProjects =
            listOf(
              Dependency(
                groupId = testMavenGroup,
                artifactId = fakeArtifactDirectoryNames[0],
                version = "asdf1132",
              ),
              Dependency(
                groupId = testMavenGroup,
                artifactId = fakeArtifactDirectoryNames[1],
                version = "a23kh1",
              ),
            ),
        )
      artifactsDirectory.addFakeArtifacts(
        fakeArtifactDirectoryNames[0],
        "hash1",
        listOf(ANDROID_AAR, SOURCES_JAR),
      )
      artifactsDirectory.addFakeArtifacts(
        fakeArtifactDirectoryNames[1],
        "hash2",
        listOf(ANDROID_AAR, SOURCES_JAR),
      )
      artifactsDirectory.addFakeArtifacts(
        fakeArtifactDirectoryNames[2],
        "hash3",
        listOf(SOURCES_JAR),
      )

      val localArtifactRepository =
        RealLocalArtifactRepository(
          xmlMapper = xmlMapper,
          ioContext = EmptyCoroutineContext,
          mavenDirectory = fakeM2Root,
          config = config,
        )
      val installedArtifacts = localArtifactRepository.getInstalledArtifacts(bom.getOrThrow())
      assertTrue(installedArtifacts.getOrThrow().isEmpty())
    }

  @Test
  fun `GIVEN bom present and some artifacts present from bom WHEN getting installed artifacts THEN correct list of installed artifacts returned`() =
    runTest {
      val bom =
        populateBom(
          artifactsDirectory,
          bomVersion = fakeBomVersion,
          dependentProjects =
            listOf(
              Dependency(
                groupId = testMavenGroup,
                artifactId = fakeArtifactDirectoryNames[0],
                version = "hash1",
              ),
              Dependency(
                groupId = testMavenGroup,
                artifactId = fakeArtifactDirectoryNames[1],
                version = "hash2",
              ),
            ),
        )
      artifactsDirectory.addFakeArtifacts(
        fakeArtifactDirectoryNames[0],
        "hash1",
        ArtifactType.entries,
      )
      val localArtifactRepository =
        RealLocalArtifactRepository(
          xmlMapper = xmlMapper,
          ioContext = EmptyCoroutineContext,
          mavenDirectory = fakeM2Root,
          config = config,
        )
      val installedArtifacts = localArtifactRepository.getInstalledArtifacts(bom.getOrThrow())
      val expected =
        setOf(InstalledArtifact(fakeArtifactDirectoryNames[0].toProjectPath(), setOf("hash1")))
      assertEquals(expected, installedArtifacts.getOrThrow())
    }

  @Test
  fun `GIVEN bom present and artifact from bom is partially installed, without sources WHEN getting installed artifacts THEN artifact without sources not returned`() =
    runTest {
      val bom =
        populateBom(
          artifactsDirectory,
          bomVersion = fakeBomVersion,
          dependentProjects =
            listOf(
              Dependency(
                groupId = testMavenGroup,
                artifactId = fakeArtifactDirectoryNames[0],
                version = "hash1",
              ),
              Dependency(
                groupId = testMavenGroup,
                artifactId = fakeArtifactDirectoryNames[1],
                version = "hash2",
              ),
              Dependency(
                groupId = testMavenGroup,
                artifactId = fakeArtifactDirectoryNames[2],
                version = "hash3",
              ),
            ),
        )
      // First artifact missing jar/aar, just pom
      artifactsDirectory.addFakeArtifacts(fakeArtifactDirectoryNames[0], "hash1", listOf(POM))
      artifactsDirectory.addFakeArtifacts(
        fakeArtifactDirectoryNames[1],
        "hash2",
        ArtifactType.entries,
      )
      artifactsDirectory.addFakeArtifacts(
        fakeArtifactDirectoryNames[2],
        "hash3",
        ArtifactType.entries,
      )
      val localArtifactRepository =
        RealLocalArtifactRepository(
          xmlMapper = xmlMapper,
          ioContext = EmptyCoroutineContext,
          mavenDirectory = fakeM2Root,
          config = config,
        )
      val installedArtifacts = localArtifactRepository.getInstalledArtifacts(bom.getOrThrow())
      val expected =
        setOf(
          InstalledArtifact(fakeArtifactDirectoryNames[1].toProjectPath(), setOf("hash2")),
          InstalledArtifact(fakeArtifactDirectoryNames[2].toProjectPath(), setOf("hash3")),
        )
      assertEquals(expected, installedArtifacts.getOrThrow())
    }

  @Test
  fun `GIVEN multiple versions of artifact installed from bom WHEN getting installed artifacts THEN only version from bom returned`() =
    runTest {
      val bom =
        populateBom(
          artifactsDirectory,
          bomVersion = fakeBomVersion,
          dependentProjects =
            listOf(
              Dependency(
                groupId = testMavenGroup,
                artifactId = fakeArtifactDirectoryNames[0],
                version = "hash1",
              ),
              Dependency(
                groupId = testMavenGroup,
                artifactId = fakeArtifactDirectoryNames[1],
                version = "hash2",
              ),
            ),
        )
      artifactsDirectory.addFakeArtifacts(
        fakeArtifactDirectoryNames[0],
        "hash1",
        ArtifactType.entries,
      )
      artifactsDirectory.addFakeArtifacts(
        fakeArtifactDirectoryNames[0],
        "hash2",
        ArtifactType.entries,
      )
      artifactsDirectory.addFakeArtifacts(
        fakeArtifactDirectoryNames[0],
        "hash3",
        ArtifactType.entries,
      )
      val localArtifactRepository =
        RealLocalArtifactRepository(
          xmlMapper = xmlMapper,
          ioContext = EmptyCoroutineContext,
          mavenDirectory = fakeM2Root,
          config = config,
        )
      val installedArtifacts = localArtifactRepository.getInstalledArtifacts(bom.getOrThrow())
      val expected =
        setOf(InstalledArtifact(fakeArtifactDirectoryNames[0].toProjectPath(), setOf("hash1")))
      assertEquals(expected, installedArtifacts.getOrThrow())
    }

  @Test
  fun `GIVEN multiple artifacts have same version installed WHEN getting installed artifacts THEN only artifact+version in bom is returned`() =
    runTest {
      val bom =
        populateBom(
          artifactsDirectory,
          bomVersion = fakeBomVersion,
          dependentProjects =
            listOf(
              Dependency(
                groupId = testMavenGroup,
                artifactId = fakeArtifactDirectoryNames[0],
                version = "hash1",
              )
            ),
        )
      artifactsDirectory.addFakeArtifacts(
        fakeArtifactDirectoryNames[0],
        "hash1",
        ArtifactType.entries,
      )
      artifactsDirectory.addFakeArtifacts(
        fakeArtifactDirectoryNames[1],
        "hash1",
        ArtifactType.entries,
      )
      val localArtifactRepository =
        RealLocalArtifactRepository(
          xmlMapper = xmlMapper,
          ioContext = EmptyCoroutineContext,
          mavenDirectory = fakeM2Root,
          config = config,
        )
      val installedArtifacts = localArtifactRepository.getInstalledArtifacts(bom.getOrThrow())
      val expected =
        setOf(InstalledArtifact(fakeArtifactDirectoryNames[0].toProjectPath(), setOf("hash1")))
      assertEquals(expected, installedArtifacts.getOrThrow())
    }

  @Test
  fun `GIVEN standard artifact WHEN getting list of installed artifacts THEN returned project path starts with colon`() =
    runTest {
      val bom =
        populateBom(
          artifactsDirectory,
          bomVersion = fakeBomVersion,
          dependentProjects =
            listOf(
              Dependency(
                groupId = testMavenGroup,
                artifactId = fakeArtifactDirectoryNames[0],
                version = "hash1",
              ),
              Dependency(
                groupId = testMavenGroup,
                artifactId = fakeArtifactDirectoryNames[1],
                version = "hash2",
              ),
            ),
        )
      artifactsDirectory.addFakeArtifacts(
        fakeArtifactDirectoryNames[0],
        "hash1",
        listOf(ANDROID_AAR, SOURCES_JAR),
      )
      val localArtifactRepository =
        RealLocalArtifactRepository(
          xmlMapper = xmlMapper,
          ioContext = EmptyCoroutineContext,
          mavenDirectory = fakeM2Root,
          config = config,
        )
      val installedArtifacts = localArtifactRepository.getInstalledArtifacts(bom.getOrThrow())
      assertTrue(installedArtifacts.getOrThrow().all { it.projectPath.startsWith(":") })
    }

  @Test
  fun `GIVEN custom primary artifacts maven group WHEN getting installed bom THEN uses configured group path`() =
    runTest {
      // Use a different maven group than the default to verify config is used
      val customMavenGroup = "org.acme.custom"
      val customConfig = testArtifactSwapConfig(primaryArtifactsMavenGroup = customMavenGroup)
      val customArtifactsDirectory = fakeM2Root.resolve("org").resolve("acme").resolve("custom")
      customArtifactsDirectory.createDirectories()

      val bomVersion = "test-123"
      val bom =
        Project(
          groupId = customMavenGroup,
          artifactId = "bom",
          version = bomVersion,
          name = "bom",
          dependencyManagement =
            DependencyManagement(
              dependencies =
                Dependencies(
                  dependency =
                    listOf(
                      Dependency(
                        groupId = customMavenGroup,
                        artifactId = "test-artifact",
                        version = "1.0.0",
                      )
                    )
                )
            ),
        )

      // Create BOM in the custom group path
      val bomPath =
        customArtifactsDirectory.resolve("bom").resolve(bomVersion).resolve("bom-$bomVersion.pom")
      bomPath.parent.createDirectories()
      bomPath.writeText(xmlMapper.writeValueAsString(bom))

      val localArtifactRepository =
        RealLocalArtifactRepository(
          xmlMapper = xmlMapper,
          ioContext = EmptyCoroutineContext,
          mavenDirectory = fakeM2Root,
          config = customConfig,
        )

      val installedBom = localArtifactRepository.getInstalledBom(bomVersion)
      assertTrue(
        installedBom.isSuccess,
        "Expected BOM to be found: ${installedBom.exceptionOrNull()}",
      )
      assertEquals(bomVersion, installedBom.getOrThrow().version)
    }

  @Test
  fun `GIVEN custom primary artifacts maven group WHEN getting installed artifacts THEN uses configured group path`() =
    runTest {
      // Use a different maven group than the default to verify config is used
      val customMavenGroup = "com.example.test.artifacts"
      val customConfig = testArtifactSwapConfig(primaryArtifactsMavenGroup = customMavenGroup)
      val customArtifactsDirectory =
        fakeM2Root.resolve("com").resolve("example").resolve("test").resolve("artifacts")
      customArtifactsDirectory.createDirectories()

      val bom =
        Project(
          groupId = customMavenGroup,
          artifactId = "test-bom",
          version = "1.0.0",
          name = "test-bom",
          dependencyManagement =
            DependencyManagement(
              dependencies =
                Dependencies(
                  dependency =
                    listOf(
                      Dependency(
                        groupId = customMavenGroup,
                        artifactId = fakeArtifactDirectoryNames[0],
                        version = "hash1",
                      )
                    )
                )
            ),
        )

      // Create artifacts in the custom group path
      customArtifactsDirectory.addFakeArtifacts(
        fakeArtifactDirectoryNames[0],
        "hash1",
        ArtifactType.entries,
      )

      val localArtifactRepository =
        RealLocalArtifactRepository(
          xmlMapper = xmlMapper,
          ioContext = EmptyCoroutineContext,
          mavenDirectory = fakeM2Root,
          config = customConfig,
        )

      val installedArtifacts = localArtifactRepository.getInstalledArtifacts(bom)
      val expected =
        setOf(InstalledArtifact(fakeArtifactDirectoryNames[0].toProjectPath(), setOf("hash1")))
      assertEquals(expected, installedArtifacts.getOrThrow())
    }

  @Test
  fun `GIVEN multiple boms installed WHEN getting list of installed artifacts THEN only returns artifacts from requested bom`() =
    runTest {
      val bom =
        populateBom(
          artifactsDirectory,
          bomVersion = fakeBomVersion,
          dependentProjects =
            listOf(
              Dependency(
                groupId = testMavenGroup,
                artifactId = fakeArtifactDirectoryNames[0],
                version = "hash1",
              ),
              Dependency(
                groupId = testMavenGroup,
                artifactId = fakeArtifactDirectoryNames[1],
                version = "hash2",
              ),
            ),
        )
      populateBom(
        artifactsDirectory = artifactsDirectory,
        bomVersion = "differentBom",
        dependentProjects =
          listOf(
            Dependency(
              groupId = testMavenGroup,
              artifactId = fakeArtifactDirectoryNames[0],
              version = "hash3",
            ),
            Dependency(
              groupId = testMavenGroup,
              artifactId = fakeArtifactDirectoryNames[1],
              version = "hash4",
            ),
          ),
      )
      artifactsDirectory.addFakeArtifacts(
        fakeArtifactDirectoryNames[0],
        "hash1",
        ArtifactType.entries,
      )
      artifactsDirectory.addFakeArtifacts(
        fakeArtifactDirectoryNames[0],
        "hash3",
        ArtifactType.entries,
      )
      artifactsDirectory.addFakeArtifacts(
        fakeArtifactDirectoryNames[1],
        "hash2",
        ArtifactType.entries,
      )
      artifactsDirectory.addFakeArtifacts(
        fakeArtifactDirectoryNames[1],
        "hash4",
        ArtifactType.entries,
      )
      val localArtifactRepository =
        RealLocalArtifactRepository(
          xmlMapper = xmlMapper,
          ioContext = EmptyCoroutineContext,
          mavenDirectory = fakeM2Root,
          config = config,
        )
      val installedArtifacts = localArtifactRepository.getInstalledArtifacts(bom.getOrThrow())
      val expected =
        setOf(
          InstalledArtifact(fakeArtifactDirectoryNames[0].toProjectPath(), setOf("hash1")),
          InstalledArtifact(fakeArtifactDirectoryNames[1].toProjectPath(), setOf("hash2")),
        )
      assertEquals(expected, installedArtifacts.getOrThrow())
    }

  /** Places a valid POM file in the expected location for the given project path and version. */
  private fun populateBom(
    artifactsDirectory: Path,
    bomVersion: String,
    dependentProjects: List<Dependency>,
  ): Result<Project> = runCatching {
    val mavenPom =
      Project(
        groupId = testMavenGroup,
        artifactId = "features_open-tickets-v2_create-ticket_impl-tablet",
        version = bomVersion,
        name = "features_open-tickets-v2_create-ticket_impl-tablet",
        dependencyManagement =
          DependencyManagement(dependencies = Dependencies(dependency = dependentProjects)),
      )
    val pathToBomFile =
      artifactsDirectory.resolve("bom").resolve(bomVersion).resolve("bom-$bomVersion.pom")
    pathToBomFile.parent.createDirectories()
    pathToBomFile.writeText(xmlMapper.writeValueAsString(mavenPom))
    mavenPom
  }
}

private fun String.toProjectPath(): String {
  return ":" + this.replace("_", ":")
}

private fun Path.addFakeArtifacts(
  projectName: String,
  hashVersion: String,
  artifactTypesToAdd: List<ArtifactType>,
) {
  val projectDirectory = resolve(projectName)
  val hashVersionDirectory = projectDirectory.resolve(hashVersion)
  hashVersionDirectory.createDirectories()
  artifactTypesToAdd.forEach { artifactType ->
    when (artifactType) {
      ArtifactType.ANDROID_AAR -> {
        val aarFile = hashVersionDirectory.resolve("$projectName-$hashVersion.aar")
        aarFile.createFile()
      }

      ArtifactType.SOURCES_JAR -> {
        val sourcesJarFile = hashVersionDirectory.resolve("$projectName-$hashVersion-sources.jar")
        sourcesJarFile.createFile()
      }

      ArtifactType.POM -> {
        val pomFile = hashVersionDirectory.resolve("$projectName-$hashVersion.pom")
        pomFile.createFile()
      }

      ArtifactType.MODULE -> {
        val moduleFile = hashVersionDirectory.resolve("$projectName-$hashVersion.module")
        moduleFile.createFile()
      }
    }
  }
}

enum class ArtifactType {
  ANDROID_AAR,
  SOURCES_JAR,
  POM,
  MODULE,
}
