package xyz.block.artifactswap.core.publisher

import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xyz.block.artifactswap.core.config.ArtifactSwapConfig
import xyz.block.artifactswap.core.maven.Metadata
import xyz.block.artifactswap.core.maven.Versioning
import xyz.block.artifactswap.core.maven.Versions
import xyz.block.artifactswap.core.publisher.models.BomPublishingResult

class BomPublisherTest {

  private lateinit var fakeHashReader: FakeProjectHashReader
  private lateinit var fakePublishedArtifactRepository: FakePublishedArtifactRepository
  private lateinit var fakeBomRepository: FakeBomRepository
  private lateinit var fakeEventStream: FakeBomPublisherEventStream
  private lateinit var bomPublisher: BomPublisher

  private val testConfig =
    ArtifactSwapConfig(
      primaryRepositoryName = "test-repo",
      primaryArtifactsMavenGroup = "xyz.block.artifactswap.artifacts",
      eventstreamBaseUrl = "https://eventstream.test.com",
      artifactoryPublisherTokenFileName = "test-token.txt",
      excludeGradleProjects = emptyList(),
      bomSourceBranchName = "origin/main",
      artifactoryBaseUrl = "https://artifactory.test.com",
    )

  private val testCiMetadata =
    CiMetadata(
      gitBranch = "test-branch",
      gitSha = "test-sha",
      kochikuEnv = "test",
      buildId = "build-123",
      buildStepId = "step-456",
      buildJobId = "job-789",
      ciType = "test",
    )

  @BeforeEach
  fun setUp() {
    fakeHashReader = FakeProjectHashReader()
    fakePublishedArtifactRepository = FakePublishedArtifactRepository()
    fakeBomRepository = FakeBomRepository()
    fakeEventStream = FakeBomPublisherEventStream()
    bomPublisher =
      BomPublisher(
        projectHashReader = fakeHashReader,
        publishedArtifactRepository = fakePublishedArtifactRepository,
        bomRepository = fakeBomRepository,
        eventStream = fakeEventStream,
        config = testConfig,
      )
  }

  @Test
  fun `GIVEN failed hash reading WHEN publishing THEN returns failure result`() = runTest {
    fakeHashReader.projectHashes = Result.failure(IOException("Failed to read"))

    val result =
      bomPublisher.publishBom(
        bomVersion = "1.0.0",
        hashPath = Path("/fake/path"),
        ciMetadata = testCiMetadata,
      )

    assertEquals(BomPublishingResult.FAILED_READING_PROJECT_HASHES, result.result)
  }

  @Test
  fun `GIVEN empty project hashes WHEN publishing THEN returns failure result`() = runTest {
    fakeHashReader.projectHashes = Result.success(emptyMap())

    val result =
      bomPublisher.publishBom(
        bomVersion = "1.0.0",
        hashPath = Path("/fake/path"),
        ciMetadata = testCiMetadata,
      )

    assertEquals(BomPublishingResult.FAILED_FETCHING_PUBLISHED_PROJECT_DATA, result.result)
  }

  @Test
  fun `GIVEN project hashes but no published artifacts WHEN publishing THEN returns failure result`() =
    runTest {
      fakeHashReader.projectHashes =
        Result.success(mapOf("artifact1" to "version1", "artifact2" to "version2"))
      // No available dependencies means artifacts not found in repository

      val result =
        bomPublisher.publishBom(
          bomVersion = "1.0.0",
          hashPath = Path("/fake/path"),
          ciMetadata = testCiMetadata,
        )

      assertEquals(BomPublishingResult.FAILED_FETCHING_PUBLISHED_PROJECT_DATA, result.result)
    }

  @Test
  fun `GIVEN published artifacts WHEN publishing THEN creates BOM with dependencies`() = runTest {
    fakeHashReader.projectHashes =
      Result.success(mapOf("artifact1" to "version1", "artifact2" to "version2"))

    // Set up available dependencies
    fakePublishedArtifactRepository.availableDependencies["artifact1"] = "version1"
    fakePublishedArtifactRepository.availableDependencies["artifact2"] = "version2"

    val result =
      bomPublisher.publishBom(
        bomVersion = "1.0.0",
        hashPath = Path("/fake/path"),
        ciMetadata = testCiMetadata,
      )

    assertTrue(
      result.result in
        listOf(
          BomPublishingResult.SUCCESS_BOM_AND_METADATA_PUBLISHED,
          BomPublishingResult.SUCCESS_BOM_PUBLISHED_METADATA_NO_UPDATE,
          BomPublishingResult.SUCCESS_BOM_PUBLISHED_METADATA_FAILED,
        )
    )
    assertEquals(1, fakeBomRepository.storedBoms.size)
    val pushedPom = fakeBomRepository.storedBoms.first()
    assertEquals("bom", pushedPom.artifactId)
    assertEquals("1.0.0", pushedPom.version)
    val depManagement =
      requireNotNull(pushedPom.dependencyManagement) {
        "Dependency management should not be null in published BOM, that's where declared versions of artifacts go!"
      }
    assertEquals(2, depManagement.dependencies.dependency.size)
  }

  @Test
  fun `GIVEN new BOM WHEN publishing THEN creates metadata`() = runTest {
    fakeHashReader.projectHashes = Result.success(mapOf("artifact1" to "version1"))

    fakePublishedArtifactRepository.availableDependencies["artifact1"] = "version1"

    val result =
      bomPublisher.publishBom(
        bomVersion = "1.0.0",
        hashPath = Path("/fake/path"),
        ciMetadata = testCiMetadata,
      )

    assertEquals(BomPublishingResult.SUCCESS_BOM_AND_METADATA_PUBLISHED, result.result)
    assertEquals(1, fakeBomRepository.storedMetadata.size)
    val pushedMetadata = fakeBomRepository.storedMetadata.first()
    assertEquals("bom", pushedMetadata.artifactId)
    assertEquals("1.0.0", pushedMetadata.versioning.latest)
    assertEquals("1.0.0", pushedMetadata.versioning.release)
  }

  @Test
  fun `GIVEN existing BOM metadata WHEN publishing THEN updates metadata`() = runTest {
    fakeHashReader.projectHashes = Result.success(mapOf("artifact1" to "version1"))

    fakePublishedArtifactRepository.availableDependencies["artifact1"] = "version1"

    // Set up existing BOM metadata
    fakeBomRepository.getMetadataResult =
      Result.success(
        Metadata(
          groupId = "xyz.block.artifactswap.artifacts",
          artifactId = "bom",
          versioning =
            Versioning(
              latest = "0.9.0",
              release = "0.9.0",
              versions = Versions(listOf("0.9.0")),
              lastUpdated = 0,
            ),
        )
      )

    val result =
      bomPublisher.publishBom(
        bomVersion = "1.0.0",
        hashPath = Path("/fake/path"),
        ciMetadata = testCiMetadata,
      )

    assertEquals(BomPublishingResult.SUCCESS_BOM_AND_METADATA_PUBLISHED, result.result)
    assertEquals(1, fakeBomRepository.storedMetadata.size)
    val pushedMetadata = fakeBomRepository.storedMetadata.first()
    assertEquals("bom", pushedMetadata.artifactId)
    assertEquals("1.0.0", pushedMetadata.versioning.latest)
    assertEquals("1.0.0", pushedMetadata.versioning.release)
    assertTrue(pushedMetadata.versioning.versions.version.contains("0.9.0"))
    assertTrue(pushedMetadata.versioning.versions.version.contains("1.0.0"))
  }

  @Test
  fun `GIVEN dry run mode WHEN publishing THEN does not push to repository`() = runTest {
    val dryRunPublisher =
      BomPublisher(
        projectHashReader = fakeHashReader,
        publishedArtifactRepository = fakePublishedArtifactRepository,
        bomRepository = fakeBomRepository,
        eventStream = fakeEventStream,
        config = testConfig,
        dryRun = true,
      )

    fakeHashReader.projectHashes = Result.success(mapOf("artifact1" to "version1"))

    fakePublishedArtifactRepository.availableDependencies["artifact1"] = "version1"

    val result =
      dryRunPublisher.publishBom(
        bomVersion = "1.0.0",
        hashPath = Path("/fake/path"),
        ciMetadata = testCiMetadata,
      )

    assertTrue(
      result.result in
        listOf(
          BomPublishingResult.SUCCESS_BOM_AND_METADATA_PUBLISHED,
          BomPublishingResult.SUCCESS_BOM_PUBLISHED_METADATA_NO_UPDATE,
        )
    )
    // In dry run mode, nothing should be published
    assertEquals(0, fakeBomRepository.storedBoms.size)
    assertEquals(0, fakeBomRepository.storedMetadata.size)
  }

  @Test
  fun `GIVEN successful publishing WHEN logging result THEN sends event to event stream`() =
    runTest {
      fakeHashReader.projectHashes = Result.success(mapOf("artifact1" to "version1"))

      fakePublishedArtifactRepository.availableDependencies["artifact1"] = "version1"

      val result =
        bomPublisher.publishBom(
          bomVersion = "1.0.0",
          hashPath = Path("/fake/path"),
          ciMetadata = testCiMetadata,
        )
      bomPublisher.logResult(result)

      assertEquals(1, fakeEventStream.receivedResults.size)
      val receivedResult = fakeEventStream.receivedResults.first()
      assertTrue(
        receivedResult.result in
          listOf(
            BomPublishingResult.SUCCESS_BOM_AND_METADATA_PUBLISHED,
            BomPublishingResult.SUCCESS_BOM_PUBLISHED_METADATA_NO_UPDATE,
          )
      )
    }
}
