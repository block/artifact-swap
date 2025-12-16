package xyz.block.artifactswap.core.download.services

import java.io.FileNotFoundException
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.lib.ObjectId
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.wheneverBlocking
import xyz.block.artifactswap.core.config.testArtifactSwapConfig
import xyz.block.artifactswap.core.maven.Dependencies
import xyz.block.artifactswap.core.maven.Dependency
import xyz.block.artifactswap.core.maven.DependencyManagement
import xyz.block.artifactswap.core.maven.Project
import xyz.block.artifactswap.core.network.ArtifactoryService
import xyz.block.artifactswap.core.shared_services.git.SquareGit

class RealArtifactSyncBomLoaderTest {

  companion object {
    private val TEST_CONFIG = testArtifactSwapConfig()

    private val DEFAULT_MAVEN_POM =
      Project(
        groupId = "com.squareup",
        artifactId = "bom",
        version = "abcd1234",
        name = "bom-name",
        dependencyManagement =
          DependencyManagement(
            dependencies =
              Dependencies(
                dependency =
                  listOf(
                    Dependency(
                      groupId = "sandbags_group_id",
                      artifactId = "a_b_c",
                      version = "abcd1234",
                    ),
                    Dependency(groupId = "com.squareup", artifactId = "d_e_f", version = "efgh5678"),
                  )
              )
          ),
      )
  }

  @Test
  fun `GIVEN checkRemote false WHEN finding best bom THEN only checks local repository`() =
    runTest {
      val mockSquareGit = mock<SquareGit>()
      val mockLocalRepo = mock<ArtifactRepository>()
      val mockArtifactory = mock<ArtifactoryService>()

      val commitHash = "e272a0091dda8d4d14056560df3dd34c45b0d94a"
      val commits = listOf(ObjectId.fromString(commitHash))

      wheneverBlocking { mockSquareGit.findRecentSharedCommits(any(), any()) }.thenReturn(commits)
      wheneverBlocking { mockLocalRepo.getInstalledBom(commitHash) }
        .thenReturn(Result.success(DEFAULT_MAVEN_POM))

      val bomLoader =
        RealArtifactSyncBomLoader(
          squareGit = mockSquareGit,
          localArtifactRepository = mockLocalRepo,
          artifactoryService = mockArtifactory,
          config = TEST_CONFIG,
        )

      val result = bomLoader.findBestBomVersion(checkRemote = false)

      assertTrue(result.isSuccess)
      assertEquals(commitHash, result.getOrThrow())

      // Verify Artifactory was never called when checkRemote is false
      verify(mockArtifactory, never()).getPom(any(), any())
    }

  @Test
  fun `GIVEN checkRemote true and BOM exists in Artifactory WHEN finding best bom THEN checks Artifactory`() =
    runTest {
      val mockSquareGit = mock<SquareGit>()
      val mockLocalRepo = mock<ArtifactRepository>()
      val mockArtifactory = mock<ArtifactoryService>()

      val commitHash = "e272a0091dda8d4d14056560df3dd34c45b0d94a"
      val commits = listOf(ObjectId.fromString(commitHash))

      wheneverBlocking { mockSquareGit.findRecentSharedCommits(any(), any()) }.thenReturn(commits)
      // BOM not found locally
      wheneverBlocking { mockLocalRepo.getInstalledBom(commitHash) }
        .thenReturn(Result.failure(FileNotFoundException("Not found locally")))
      // BOM found in Artifactory
      wheneverBlocking { mockArtifactory.getPom(any(), any()) }.thenReturn(DEFAULT_MAVEN_POM)

      val bomLoader =
        RealArtifactSyncBomLoader(
          squareGit = mockSquareGit,
          localArtifactRepository = mockLocalRepo,
          artifactoryService = mockArtifactory,
          config = TEST_CONFIG,
        )

      val result = bomLoader.findBestBomVersion(checkRemote = true)

      assertTrue(result.isSuccess)
      assertEquals(commitHash, result.getOrThrow())

      // Verify Artifactory was called with correct parameters
      verify(mockArtifactory).getPom(artifactName = "bom", version = commitHash)
    }

  @Test
  fun `GIVEN checkRemote true and BOM exists locally WHEN finding best bom THEN finds it without calling Artifactory`() =
    runTest {
      val mockSquareGit = mock<SquareGit>()
      val mockLocalRepo = mock<ArtifactRepository>()
      val mockArtifactory = mock<ArtifactoryService>()

      val commitHash = "e272a0091dda8d4d14056560df3dd34c45b0d94a"
      val commits = listOf(ObjectId.fromString(commitHash))

      wheneverBlocking { mockSquareGit.findRecentSharedCommits(any(), any()) }.thenReturn(commits)
      // BOM found locally
      wheneverBlocking { mockLocalRepo.getInstalledBom(commitHash) }
        .thenReturn(Result.success(DEFAULT_MAVEN_POM))

      val bomLoader =
        RealArtifactSyncBomLoader(
          squareGit = mockSquareGit,
          localArtifactRepository = mockLocalRepo,
          artifactoryService = mockArtifactory,
          config = TEST_CONFIG,
        )

      val result = bomLoader.findBestBomVersion(checkRemote = true)

      assertTrue(result.isSuccess)
      assertEquals(commitHash, result.getOrThrow())

      // Verify Artifactory was not called since BOM was found locally
      verify(mockArtifactory, never()).getPom(any(), any())
    }

  @Test
  fun `GIVEN checkRemote true and first commit has no BOM but second has remote BOM WHEN finding best bom THEN returns second commit`() =
    runTest {
      val mockSquareGit = mock<SquareGit>()
      val mockLocalRepo = mock<ArtifactRepository>()
      val mockArtifactory = mock<ArtifactoryService>()

      val commitHash1 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      val commitHash2 = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
      val commits = listOf(ObjectId.fromString(commitHash1), ObjectId.fromString(commitHash2))

      wheneverBlocking { mockSquareGit.findRecentSharedCommits(any(), any()) }.thenReturn(commits)
      // Neither commit has local BOM
      wheneverBlocking { mockLocalRepo.getInstalledBom(any()) }
        .thenReturn(Result.failure(FileNotFoundException("Not found locally")))
      // Setup Artifactory to throw for first commit, return for second
      wheneverBlocking { mockArtifactory.getPom(any(), any()) }
        .thenAnswer { invocation ->
          when (val version = invocation.getArgument<String>(1)) {
            commitHash1 -> throw FileNotFoundException("Not found")
            commitHash2 -> DEFAULT_MAVEN_POM
            else -> throw IllegalArgumentException("Unexpected version: $version")
          }
        }

      val bomLoader =
        RealArtifactSyncBomLoader(
          squareGit = mockSquareGit,
          localArtifactRepository = mockLocalRepo,
          artifactoryService = mockArtifactory,
          config = TEST_CONFIG,
        )

      val result = bomLoader.findBestBomVersion(checkRemote = true)

      assertTrue(result.isSuccess)
      assertEquals(commitHash2, result.getOrThrow())

      // Verify both commits were checked in Artifactory
      verify(mockArtifactory).getPom(artifactName = "bom", version = commitHash1)
      verify(mockArtifactory).getPom(artifactName = "bom", version = commitHash2)
    }

  @Test
  fun `GIVEN findBestBomVersion returns failure WHEN no commits found THEN returns failure`() =
    runTest {
      val mockSquareGit = mock<SquareGit>()
      val mockLocalRepo = mock<ArtifactRepository>()
      val mockArtifactory = mock<ArtifactoryService>()

      wheneverBlocking { mockSquareGit.findRecentSharedCommits(any(), any()) }.thenReturn(null)

      val bomLoader =
        RealArtifactSyncBomLoader(
          squareGit = mockSquareGit,
          localArtifactRepository = mockLocalRepo,
          artifactoryService = mockArtifactory,
          config = TEST_CONFIG,
        )

      val result = bomLoader.findBestBomVersion(checkRemote = false)
      assertTrue(result.isFailure)
    }

  @Test
  fun `GIVEN loadBom WHEN BOM available locally THEN returns local BOM`() = runTest {
    val mockSquareGit = mock<SquareGit>()
    val mockLocalRepo = mock<ArtifactRepository>()
    val mockArtifactory = mock<ArtifactoryService>()

    val bomVersion = "abcd1234"
    wheneverBlocking { mockLocalRepo.getInstalledBom(bomVersion) }
      .thenReturn(Result.success(DEFAULT_MAVEN_POM))

    val bomLoader =
      RealArtifactSyncBomLoader(
        squareGit = mockSquareGit,
        localArtifactRepository = mockLocalRepo,
        artifactoryService = mockArtifactory,
        config = TEST_CONFIG,
      )

    val result = bomLoader.loadBom(bomVersion)
    assertTrue(result.isSuccess)
    assertEquals(DEFAULT_MAVEN_POM, result.getOrThrow())

    // Verify Artifactory was not called since BOM was found locally
    verify(mockArtifactory, never()).getPom(any(), any())
  }

  @Test
  fun `GIVEN loadBom WHEN BOM not available locally but in Artifactory THEN fetches from Artifactory`() =
    runTest {
      val mockSquareGit = mock<SquareGit>()
      val mockLocalRepo = mock<ArtifactRepository>()
      val mockArtifactory = mock<ArtifactoryService>()

      val bomVersion = "abcd1234"
      wheneverBlocking { mockLocalRepo.getInstalledBom(bomVersion) }
        .thenReturn(Result.failure(FileNotFoundException("Not found locally")))
      wheneverBlocking { mockArtifactory.getPom(artifactName = "bom", version = bomVersion) }
        .thenReturn(DEFAULT_MAVEN_POM)

      val bomLoader =
        RealArtifactSyncBomLoader(
          squareGit = mockSquareGit,
          localArtifactRepository = mockLocalRepo,
          artifactoryService = mockArtifactory,
          config = TEST_CONFIG,
        )

      val result = bomLoader.loadBom(bomVersion)
      assertTrue(result.isSuccess)
      assertEquals(DEFAULT_MAVEN_POM, result.getOrThrow())

      // Verify Artifactory was called with correct parameters
      verify(mockArtifactory).getPom(artifactName = "bom", version = bomVersion)
    }

  @Test
  fun `GIVEN loadBom WHEN BOM not available anywhere THEN returns failure`() = runTest {
    val mockSquareGit = mock<SquareGit>()
    val mockLocalRepo = mock<ArtifactRepository>()
    val mockArtifactory = mock<ArtifactoryService>()

    val bomVersion = "nonexistent"
    wheneverBlocking { mockLocalRepo.getInstalledBom(bomVersion) }
      .thenReturn(Result.failure(RuntimeException("Not found locally")))
    wheneverBlocking { mockArtifactory.getPom(artifactName = "bom", version = bomVersion) }
      .thenThrow(RuntimeException("Not found in Artifactory"))

    val bomLoader =
      RealArtifactSyncBomLoader(
        squareGit = mockSquareGit,
        localArtifactRepository = mockLocalRepo,
        artifactoryService = mockArtifactory,
        config = TEST_CONFIG,
      )

    val result = bomLoader.loadBom(bomVersion)
    assertTrue(result.isFailure)
  }
}
