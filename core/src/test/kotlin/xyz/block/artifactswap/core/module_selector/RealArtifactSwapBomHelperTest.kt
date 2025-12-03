package xyz.block.artifactswap.core.module_selector

import java.io.FileNotFoundException
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.lib.ObjectId
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import xyz.block.artifactswap.core.maven.Dependencies
import xyz.block.artifactswap.core.maven.Dependency
import xyz.block.artifactswap.core.maven.DependencyManagement
import xyz.block.artifactswap.core.maven.Project
import xyz.block.artifactswap.core.network.ArtifactoryService

class RealArtifactSwapBomHelperTest {

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

  @Test
  fun `findBestBomVersion finds BOM version from git commits`() = runTest {
    val mockSquareGit = mock<SquareGit>()
    val mockLocalRepo = mock<LocalArtifactRepository>()
    val mockArtifactory = mock<ArtifactoryService>()

    val commitHash = "e272a0091dda8d4d14056560df3dd34c45b0d94a"
    val commits = listOf(ObjectId.fromString(commitHash))

    whenever(mockSquareGit.findRecentSharedCommits(any(), any())).thenReturn(commits)
    whenever(mockLocalRepo.getInstalledBom(commitHash))
      .thenReturn(Result.success(DEFAULT_MAVEN_POM))

    val bomHelper =
      RealArtifactSwapBomHelper(
        squareGit = mockSquareGit,
        localArtifactRepository = mockLocalRepo,
        artifactoryService = mockArtifactory,
      )

    val result = bomHelper.findBestBomVersion()
    assertTrue(result.isSuccess)
    assertEquals(commitHash, result.getOrThrow())
  }

  @Test
  fun `findBestBomVersion returns failure when no commits found`() = runTest {
    val mockSquareGit = mock<SquareGit>()
    val mockLocalRepo = mock<LocalArtifactRepository>()
    val mockArtifactory = mock<ArtifactoryService>()

    whenever(mockSquareGit.findRecentSharedCommits(any(), any())).thenReturn(null)

    val bomHelper =
      RealArtifactSwapBomHelper(
        squareGit = mockSquareGit,
        localArtifactRepository = mockLocalRepo,
        artifactoryService = mockArtifactory,
      )

    val result = bomHelper.findBestBomVersion()
    assertTrue(result.isFailure)
  }

  @Test
  fun `loadBom returns local BOM when available`() = runTest {
    val mockSquareGit = mock<SquareGit>()
    val mockLocalRepo = mock<LocalArtifactRepository>()
    val mockArtifactory = mock<ArtifactoryService>()

    val bomVersion = "abcd1234"
    whenever(mockLocalRepo.getInstalledBom(bomVersion))
      .thenReturn(Result.success(DEFAULT_MAVEN_POM))

    val bomHelper =
      RealArtifactSwapBomHelper(
        squareGit = mockSquareGit,
        localArtifactRepository = mockLocalRepo,
        artifactoryService = mockArtifactory,
      )

    val result = bomHelper.loadBom(bomVersion)
    assertTrue(result.isSuccess)
    assertEquals(DEFAULT_MAVEN_POM, result.getOrThrow())
  }

  @Test
  fun `loadBom falls back to Artifactory when local BOM not available`() = runTest {
    val mockSquareGit = mock<SquareGit>()
    val mockLocalRepo = mock<LocalArtifactRepository>()
    val mockArtifactory = mock<ArtifactoryService>()

    val bomVersion = "abcd1234"
    whenever(mockLocalRepo.getInstalledBom(bomVersion))
      .thenReturn(Result.failure(FileNotFoundException("Not found locally")))
    whenever(mockArtifactory.getPom("bom", bomVersion)).thenReturn(DEFAULT_MAVEN_POM)

    val bomHelper =
      RealArtifactSwapBomHelper(
        squareGit = mockSquareGit,
        localArtifactRepository = mockLocalRepo,
        artifactoryService = mockArtifactory,
      )

    val result = bomHelper.loadBom(bomVersion)
    assertTrue(result.isSuccess)
    assertEquals(DEFAULT_MAVEN_POM, result.getOrThrow())
  }

  @Test
  fun `loadBom returns failure when BOM not available locally or remotely`() = runTest {
    val mockSquareGit = mock<SquareGit>()
    val mockLocalRepo = mock<LocalArtifactRepository>()
    val mockArtifactory = mock<ArtifactoryService>()

    val bomVersion = "nonexistent"
    whenever(mockLocalRepo.getInstalledBom(bomVersion))
      .thenReturn(Result.failure(RuntimeException("Not found locally")))
    whenever(mockArtifactory.getPom("bom", bomVersion))
      .thenThrow(RuntimeException("Not found in Artifactory"))

    val bomHelper =
      RealArtifactSwapBomHelper(
        squareGit = mockSquareGit,
        localArtifactRepository = mockLocalRepo,
        artifactoryService = mockArtifactory,
      )

    val result = bomHelper.loadBom(bomVersion)
    assertTrue(result.isFailure)
  }
}
