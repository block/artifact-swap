package xyz.block.artifactswap.core.download.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File
import kotlin.io.path.readBytes
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import xyz.block.artifactswap.core.config.testArtifactSwapConfig
import xyz.block.artifactswap.core.download.models.Artifact
import xyz.block.artifactswap.core.download.models.DownloadFileType
import xyz.block.artifactswap.core.download.models.DownloadedArtifactFileResult
import xyz.block.artifactswap.core.download.models.InstallArtifactFilesResult
import xyz.block.artifactswap.core.network.ArtifactoryEndpoints

/**
 * Tests for [RealArtifactRepository], focusing on the installation process and proper handling of
 * temp files during concurrent operations.
 */
class RealArtifactRepositoryTest {

  @TempDir lateinit var tempMavenDir: File

  private lateinit var repository: RealArtifactRepository
  private lateinit var mockArtifactoryService: ArtifactoryEndpoints
  private lateinit var objectMapper: ObjectMapper
  private val testConfig = testArtifactSwapConfig()

  @BeforeEach
  fun setUp() {
    mockArtifactoryService = mock()
    objectMapper = XmlMapper().registerKotlinModule()
    repository =
      RealArtifactRepository(
        localMavenPath = tempMavenDir.toPath(),
        artifactoryService = mockArtifactoryService,
        ioDispatcher = Dispatchers.IO,
        objectMapper = objectMapper,
        config = testConfig,
      )
  }

  @Test
  fun `GIVEN successfully downloaded files WHEN installing THEN files are written to local maven repo`() =
    runTest {
      val artifact =
        Artifact(
          groupId = "com.example.test",
          artifactId = "test-library",
          version = "1.0.0",
          repo = "test-repo",
        )

      val pomContent = "<project>test pom content</project>"
      val jarContent = "fake jar binary content"
      val moduleContent = """{"formatVersion": "1.1"}"""

      val downloadedFiles =
        listOf(
          createSuccessResult(artifact, DownloadFileType.POM, pomContent),
          createSuccessResult(artifact, DownloadFileType.JAR, jarContent),
          createSuccessResult(artifact, DownloadFileType.MODULE, moduleContent),
        )

      val result = repository.installDownloadedArtifactFiles(downloadedFiles)

      assertTrue(result is InstallArtifactFilesResult.Success, "Installation should succeed")

      // Verify files were written to correct locations
      val basePath = tempMavenDir.toPath().resolve("com/example/test/test-library/1.0.0")

      val pomFile = basePath.resolve("test-library-1.0.0.pom")
      val jarFile = basePath.resolve("test-library-1.0.0.jar")
      val moduleFile = basePath.resolve("test-library-1.0.0.module")

      assertTrue(pomFile.toFile().exists(), "POM file should exist")
      assertTrue(jarFile.toFile().exists(), "JAR file should exist")
      assertTrue(moduleFile.toFile().exists(), "Module file should exist")

      // Verify file contents
      assertEquals(pomContent, String(pomFile.readBytes()))
      assertEquals(jarContent, String(jarFile.readBytes()))
      assertEquals(moduleContent, String(moduleFile.readBytes()))
    }

  @Test
  fun `GIVEN multiple artifacts WHEN installing concurrently THEN all files are installed successfully`() =
    runTest {
      // Create multiple artifacts with multiple files each to test concurrent installation
      val artifacts =
        (1..5).map { index ->
          Artifact(
            groupId = "com.example.concurrent",
            artifactId = "library-$index",
            version = "1.0.0",
            repo = "test-repo",
          )
        }

      val downloadedFiles =
        artifacts.flatMap { artifact ->
          listOf(
            createSuccessResult(
              artifact,
              DownloadFileType.POM,
              "<project>${artifact.artifactId}</project>",
            ),
            createSuccessResult(
              artifact,
              DownloadFileType.JAR,
              "jar content for ${artifact.artifactId}",
            ),
            createSuccessResult(
              artifact,
              DownloadFileType.MODULE,
              """{"artifact":"${artifact.artifactId}"}""",
            ),
            createSuccessResult(
              artifact,
              DownloadFileType.SOURCES_JAR,
              "sources for ${artifact.artifactId}",
            ),
          )
        }

      val result = repository.installDownloadedArtifactFiles(downloadedFiles)

      assertTrue(
        result is InstallArtifactFilesResult.Success,
        "All concurrent installations should succeed",
      )

      // Verify all files were created
      artifacts.forEach { artifact ->
        val basePath =
          tempMavenDir.toPath().resolve("com/example/concurrent/${artifact.artifactId}/1.0.0")

        assertTrue(
          basePath.resolve("${artifact.artifactId}-1.0.0.pom").toFile().exists(),
          "POM for ${artifact.artifactId} should exist",
        )
        assertTrue(
          basePath.resolve("${artifact.artifactId}-1.0.0.jar").toFile().exists(),
          "JAR for ${artifact.artifactId} should exist",
        )
        assertTrue(
          basePath.resolve("${artifact.artifactId}-1.0.0.module").toFile().exists(),
          "Module for ${artifact.artifactId} should exist",
        )
        assertTrue(
          basePath.resolve("${artifact.artifactId}-1.0.0-sources.jar").toFile().exists(),
          "Sources JAR for ${artifact.artifactId} should exist",
        )
      }
    }

  @Test
  fun `GIVEN same artifact with multiple file types WHEN installing THEN all file types are installed without conflicts`() =
    runTest {
      // This test specifically verifies the fix for temp file conflicts when the same artifact
      // has multiple file types being installed concurrently
      val artifact =
        Artifact(
          groupId = "com.example.multifile",
          artifactId = "test-artifact",
          version = "VERY_LONG_VERSION_HASH_12345678901234567890",
          repo = "test-repo",
        )

      // Create all possible file types for the same artifact to maximize concurrent operations
      val downloadedFiles =
        DownloadFileType.entries.map { fileType ->
          createSuccessResult(
            artifact,
            fileType,
            "content for ${artifact.artifactId}${fileType.pathSuffix}",
          )
        }

      val result = repository.installDownloadedArtifactFiles(downloadedFiles)

      assertTrue(
        result is InstallArtifactFilesResult.Success,
        "Installation of all file types should succeed without temp file conflicts",
      )

      // Verify all file types were created
      val basePath =
        tempMavenDir.toPath().resolve("com/example/multifile/test-artifact/${artifact.version}")

      DownloadFileType.entries.forEach { fileType ->
        val file = basePath.resolve("test-artifact-${artifact.version}${fileType.pathSuffix}")
        assertTrue(file.toFile().exists(), "File for ${fileType.pathSuffix} should exist")
        val expectedContent = "content for ${artifact.artifactId}${fileType.pathSuffix}"
        assertEquals(expectedContent, String(file.readBytes()))
      }
    }

  @Test
  fun `GIVEN no successful downloads WHEN installing THEN returns NoOp`() = runTest {
    val downloadedFiles =
      listOf(
        DownloadedArtifactFileResult.NoFileExists(
          Artifact("com.example", "test", "1.0.0", "repo"),
          DownloadFileType.POM,
        ),
        DownloadedArtifactFileResult.Failure(
          Artifact("com.example", "test", "1.0.0", "repo"),
          DownloadFileType.JAR,
          Exception("Download failed"),
          kotlin.time.Duration.ZERO,
        ),
      )

    val result = repository.installDownloadedArtifactFiles(downloadedFiles)

    assertTrue(
      result is InstallArtifactFilesResult.NoOp,
      "Should return NoOp when no files to install",
    )
  }

  @Test
  fun `GIVEN empty list WHEN installing THEN returns NoOp`() = runTest {
    val result = repository.installDownloadedArtifactFiles(emptyList())

    assertTrue(result is InstallArtifactFilesResult.NoOp, "Should return NoOp for empty list")
  }

  @Test
  fun `GIVEN file overwrite scenario WHEN installing THEN existing files are overwritten`() =
    runTest {
      val artifact =
        Artifact(
          groupId = "com.example.overwrite",
          artifactId = "test-lib",
          version = "2.0.0",
          repo = "test-repo",
        )

      // First installation
      val firstContent = "original content"
      val firstDownload = listOf(createSuccessResult(artifact, DownloadFileType.JAR, firstContent))
      repository.installDownloadedArtifactFiles(firstDownload)

      val jarPath =
        tempMavenDir.toPath().resolve("com/example/overwrite/test-lib/2.0.0/test-lib-2.0.0.jar")
      assertEquals(firstContent, String(jarPath.readBytes()))

      // Second installation with different content
      val secondContent = "updated content"
      val secondDownload =
        listOf(createSuccessResult(artifact, DownloadFileType.JAR, secondContent))
      val result = repository.installDownloadedArtifactFiles(secondDownload)

      assertTrue(result is InstallArtifactFilesResult.Success, "Overwrite should succeed")
      assertEquals(secondContent, String(jarPath.readBytes()), "File should be overwritten")
    }

  @Test
  fun `GIVEN deeply nested groupId WHEN installing THEN creates all parent directories`() =
    runTest {
      val artifact =
        Artifact(
          groupId = "com.example.very.deep.nested.package.structure",
          artifactId = "deep-lib",
          version = "1.0.0",
          repo = "test-repo",
        )

      val downloadedFiles =
        listOf(createSuccessResult(artifact, DownloadFileType.JAR, "deep content"))

      val result = repository.installDownloadedArtifactFiles(downloadedFiles)

      assertTrue(result is InstallArtifactFilesResult.Success, "Installation should succeed")

      val expectedPath =
        tempMavenDir
          .toPath()
          .resolve(
            "com/example/very/deep/nested/package/structure/deep-lib/1.0.0/deep-lib-1.0.0.jar"
          )
      assertTrue(expectedPath.toFile().exists(), "File should exist in deeply nested directory")
    }

  @Test
  fun `GIVEN AAR file WHEN installing THEN AAR is written correctly`() = runTest {
    val artifact =
      Artifact(
        groupId = "com.example.android",
        artifactId = "android-lib",
        version = "1.0.0",
        repo = "test-repo",
      )

    val aarContent = "fake android archive content"
    val downloadedFiles = listOf(createSuccessResult(artifact, DownloadFileType.AAR, aarContent))

    val result = repository.installDownloadedArtifactFiles(downloadedFiles)

    assertTrue(result is InstallArtifactFilesResult.Success, "AAR installation should succeed")

    val aarPath =
      tempMavenDir.toPath().resolve("com/example/android/android-lib/1.0.0/android-lib-1.0.0.aar")
    assertTrue(aarPath.toFile().exists(), "AAR file should exist")
    assertEquals(aarContent, String(aarPath.readBytes()))
  }

  /** Helper function to create a successful download result with mock ResponseBody */
  private fun createSuccessResult(
    artifact: Artifact,
    fileType: DownloadFileType,
    content: String,
  ): DownloadedArtifactFileResult.Success {
    // Use extension function format (content first) to avoid deprecation warning
    val responseBody = content.encodeUtf8().toResponseBody(null)
    return DownloadedArtifactFileResult.Success(
      artifact = artifact,
      downloadFileType = fileType,
      fileContents = responseBody,
      fileContentsSizeBytes = content.length.toLong(),
      downloadDurationMs = kotlin.time.Duration.ZERO,
    )
  }
}
