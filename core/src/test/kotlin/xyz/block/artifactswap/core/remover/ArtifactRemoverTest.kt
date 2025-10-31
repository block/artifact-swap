package xyz.block.artifactswap.core.remover

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xyz.block.artifactswap.core.remover.models.ArtifactRemoverEventResult
import xyz.block.artifactswap.core.remover.services.InstalledBom
import xyz.block.artifactswap.core.remover.services.InstalledProject
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArtifactRemoverTest {

    private lateinit var fakeEventStream: FakeArtifactRemoverEventStream
    private lateinit var fakeRepository: FakeLocalArtifactRepository
    private lateinit var remover: ArtifactRemover

    @BeforeEach
    fun setUp() {
        fakeEventStream = FakeArtifactRemoverEventStream()
        fakeRepository = FakeLocalArtifactRepository()
        remover = ArtifactRemover(
            artifactEventStream = fakeEventStream,
            artifactRepository = fakeRepository
        )
    }

    @Test
    fun `GIVEN no installed artifacts WHEN executing THEN succeeds with no deletions`() = runTest {
        val result = remover.removeArtifacts()

        assertEquals(ArtifactRemoverEventResult.SUCCESS, result.result)
        assertTrue(fakeRepository.deletedProjects.isEmpty())
        assertTrue(fakeRepository.deletedBoms.isEmpty())
    }

    @Test
    fun `GIVEN old BOMs beyond retention WHEN executing THEN deletes old BOMs and keeps recent ones`() = runTest {
        val recentBoms = (0 until ArtifactRemover.NUMBER_OF_BOMS_TO_KEEP).map { index ->
            createTestBom("recent-$index")
        }
        val oldBoms = (0 until 5).map { index ->
            createTestBom("old-$index")
        }
        fakeRepository.installedBoms = recentBoms + oldBoms

        val result = remover.removeArtifacts()

        assertEquals(ArtifactRemoverEventResult.SUCCESS, result.result)
        assertEquals(5, fakeRepository.deletedBoms.size)
        assertTrue(fakeRepository.deletedBoms.all { it.version.startsWith("old-") })
        assertEquals(5, result.deleteOldBomsResult?.successfulDeletionBoms?.size)
        assertEquals(0, result.deleteOldBomsResult?.failedDeletionBoms?.size)
    }

    @Test
    fun `GIVEN artifacts not in recent BOMs WHEN executing THEN deletes old artifacts`() = runTest {
        val recentBom = createTestBom("recent", listOf(
            createTestProject(":app", "1.0.0"),
            createTestProject(":lib", "2.0.0")
        ))
        fakeRepository.installedBoms = listOf(recentBom)

        // Create installed projects with multiple versions
        fakeRepository.installedProjects = listOf(
            createTestProject(":app", setOf("1.0.0", "0.9.0", "0.8.0")),
            createTestProject(":lib", setOf("2.0.0", "1.5.0")),
            createTestProject(":old-module", setOf("1.0.0")) // Not in any BOM
        )

        val result = remover.removeArtifacts()

        assertEquals(ArtifactRemoverEventResult.SUCCESS, result.result)

        // Should attempt to delete old versions of :app, :lib, and all of :old-module
        assertEquals(3, fakeRepository.deletedProjects.size)

        // Verify :app had old versions deleted
        val appDeletion = fakeRepository.deletedProjects.find { it.projectPath == ":app" }
        assertEquals(setOf("0.9.0", "0.8.0"), appDeletion?.versions)

        // Verify :lib had old version deleted
        val libDeletion = fakeRepository.deletedProjects.find { it.projectPath == ":lib" }
        assertEquals(setOf("1.5.0"), libDeletion?.versions)

        // Verify :old-module was completely deleted
        val oldModuleDeletion = fakeRepository.deletedProjects.find { it.projectPath == ":old-module" }
        assertEquals(setOf("1.0.0"), oldModuleDeletion?.versions)
    }

    @Test
    fun `GIVEN deletion failures WHEN executing THEN reports failures correctly`() = runTest {
        val bom1 = createTestBom("bom-1")
        val bom2 = createTestBom("bom-2")
        fakeRepository.installedBoms = List(ArtifactRemover.NUMBER_OF_BOMS_TO_KEEP + 2) { createTestBom("keep-$it") } +
                listOf(bom1, bom2)

        // Configure bom1 to fail deletion
        fakeRepository.bomDeletionFailures.add(bom1)

        val project1 = createTestProject(":module", setOf("1.0.0"))
        fakeRepository.installedProjects = listOf(project1)
        fakeRepository.projectDeletionFailures.add(project1)

        val result = remover.removeArtifacts()

        assertEquals(ArtifactRemoverEventResult.SUCCESS, result.result)

        // Should have 1 failed BOM deletion and 3 successful
        assertEquals(1, result.deleteOldBomsResult?.failedDeletionBoms?.size)
        assertEquals(3, result.deleteOldBomsResult?.successfulDeletionBoms?.size)

        // Project deletion should have partial success (half versions deleted)
        assertTrue(result.deleteOldArtifactsResult?.failedDeletion?.isNotEmpty() == true)
    }

    @Test
    fun `GIVEN repository measurements WHEN executing THEN captures stats before and after`() = runTest {
        fakeRepository.installedBoms = listOf(createTestBom("test"))
        fakeRepository.installedProjects = listOf(createTestProject(":app", setOf("1.0.0")))

        val result = remover.removeArtifacts()

        assertEquals(ArtifactRemoverEventResult.SUCCESS, result.result)

        // Verify we captured repo stats
        assertTrue(result.startRepoStats != null)
        assertTrue(result.endRepoStats != null)

        // Verify stats have reasonable values
        assertEquals(1, result.startRepoStats?.countInstalledProjects)
        assertEquals(1, result.startRepoStats?.countInstalledBoms)
        assertTrue(result.startRepoStats?.measurementDuration != null)
    }

    @Test
    fun `GIVEN successful execution WHEN logging result THEN sends event to event stream`() = runTest {
        fakeRepository.installedBoms = listOf(createTestBom("test"))

        val result = remover.removeArtifacts()
        remover.logResult(result)

        assertEquals(1, fakeEventStream.receivedResults.size)
        val receivedResult = fakeEventStream.receivedResults.first()
        assertEquals(ArtifactRemoverEventResult.SUCCESS, receivedResult.result)
    }

    @Test
    fun `GIVEN custom BOM retention count WHEN executing THEN respects the count`() = runTest {
        val customRetentionCount = 5
        val recentBoms = (0 until customRetentionCount).map { createTestBom("recent-$it") }
        val oldBoms = (0 until 3).map { createTestBom("old-$it") }
        fakeRepository.installedBoms = recentBoms + oldBoms

        val result = remover.removeArtifacts(numberOfBomsToKeep = customRetentionCount)

        assertEquals(ArtifactRemoverEventResult.SUCCESS, result.result)
        assertEquals(3, fakeRepository.deletedBoms.size)
        assertEquals(3, result.deleteOldBomsResult?.successfulDeletionBoms?.size)
    }

    @Test
    fun `GIVEN artifacts in multiple recent BOMs WHEN executing THEN keeps all versions in use`() = runTest {
        val bom1 = createTestBom("bom-1", listOf(
            createTestProject(":app", "1.0.0")
        ))
        val bom2 = createTestBom("bom-2", listOf(
            createTestProject(":app", "1.1.0")
        ))
        fakeRepository.installedBoms = listOf(bom1, bom2)

        fakeRepository.installedProjects = listOf(
            createTestProject(":app", setOf("1.0.0", "1.1.0", "0.9.0"))
        )

        val result = remover.removeArtifacts()

        assertEquals(ArtifactRemoverEventResult.SUCCESS, result.result)

        // Should only delete 0.9.0, keeping both 1.0.0 and 1.1.0
        val appDeletion = fakeRepository.deletedProjects.find { it.projectPath == ":app" }
        assertEquals(setOf("0.9.0"), appDeletion?.versions)
    }

    // Helper functions
    private fun createTestBom(
        version: String,
        projects: List<InstalledProject> = emptyList()
    ): InstalledBom {
        return InstalledBom(
            version = version,
            repositoryPath = Path("/fake/path/$version"),
            installedProjects = projects
        )
    }

    private fun createTestProject(
        projectPath: String,
        version: String
    ): InstalledProject {
        return InstalledProject(
            projectPath = projectPath,
            repositoryPath = Path("/fake/path/$projectPath"),
            versions = setOf(version)
        )
    }

    private fun createTestProject(
        projectPath: String,
        versions: Set<String>
    ): InstalledProject {
        return InstalledProject(
            projectPath = projectPath,
            repositoryPath = Path("/fake/path/$projectPath"),
            versions = versions
        )
    }
}
