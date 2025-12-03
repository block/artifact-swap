package xyz.block.artifactswap.core.module_selector

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import xyz.block.artifactswap.core.eventstream.Eventstream

class RealArtifactSwapModuleSelectorTest {

  @TempDir lateinit var rootDir: Path

  private val fakeLocalArtifactRepository = FakeLocalArtifactRepository()
  private val fakeSquareGit = FakeSquareGit()
  private val fakeBomHelper = FakeArtifactSwapBomHelper()
  private val fakeEventstream = mock<Eventstream>()

  @Test
  fun `explicitly requested projects are always included`() = runTest {
    val selector =
      RealArtifactSwapModuleSelector(
        localArtifactRepository = fakeLocalArtifactRepository,
        squareGit = fakeSquareGit,
        artifactSwapBomHelper = fakeBomHelper,
        ioDispatcher = Dispatchers.Unconfined,
        eventstream = fakeEventstream,
      )

    val module1 = GradlePath(rootDir, ":module:1")
    val module2 = GradlePath(rootDir, ":module:2")
    val candidates = setOf(module1, module2)

    // Both modules have artifacts and no changes, but module 1 is explicitly requested
    fakeLocalArtifactRepository.installedArtifacts =
      setOf(
        InstalledArtifact(":module:1", setOf("abc123")),
        InstalledArtifact(":module:2", setOf("def456")),
      )
    fakeSquareGit.changedFiles = emptySet()

    val result = selector.selectProjects(candidates, setOf(module1))

    assertEquals(setOf(":module:1"), result.selectedProjects.map { it.path }.toSet())
    assertEquals(1, result.metrics.selectedDueToExplicitRequest)
    assertEquals(0, result.metrics.selectedDueToLocalChanges)
    assertEquals(0, result.metrics.selectedDueToMissingArtifact)
    assertEquals(1, result.metrics.excludedDueToArtifactAvailable)
    assertEquals("test-bom-version", result.bomVersion)
  }

  @Test
  fun `projects with local changes are included`() = runTest {
    val selector =
      RealArtifactSwapModuleSelector(
        localArtifactRepository = fakeLocalArtifactRepository,
        squareGit = fakeSquareGit,
        artifactSwapBomHelper = fakeBomHelper,
        ioDispatcher = Dispatchers.Unconfined,
        eventstream = fakeEventstream,
      )

    val module1 = GradlePath(rootDir, ":module:1")
    val module2 = GradlePath(rootDir, ":module:2")
    val candidates = setOf(module1, module2)

    // Both modules have artifacts, but module 2 has local changes
    fakeLocalArtifactRepository.installedArtifacts =
      setOf(
        InstalledArtifact(":module:1", setOf("abc123")),
        InstalledArtifact(":module:2", setOf("def456")),
      )
    fakeSquareGit.changedFiles = setOf(rootDir.resolve("module/2/SomeFile.kt"))

    val result = selector.selectProjects(candidates, setOf(module1))

    // Module 1 explicitly requested, Module 2 has changes
    assertEquals(setOf(":module:1", ":module:2"), result.selectedProjects.map { it.path }.toSet())
    assertEquals(1, result.metrics.selectedDueToExplicitRequest)
    assertEquals(1, result.metrics.selectedDueToLocalChanges)
    assertEquals(0, result.metrics.selectedDueToMissingArtifact)
  }

  @Test
  fun `projects without local artifacts are included`() = runTest {
    val selector =
      RealArtifactSwapModuleSelector(
        localArtifactRepository = fakeLocalArtifactRepository,
        squareGit = fakeSquareGit,
        artifactSwapBomHelper = fakeBomHelper,
        ioDispatcher = Dispatchers.Unconfined,
        eventstream = fakeEventstream,
      )

    val module1 = GradlePath(rootDir, ":module:1")
    val module2 = GradlePath(rootDir, ":module:2")
    val candidates = setOf(module1, module2)

    // Only module 1 has an artifact
    fakeLocalArtifactRepository.installedArtifacts =
      setOf(InstalledArtifact(":module:1", setOf("abc123")))
    fakeSquareGit.changedFiles = emptySet()

    val result = selector.selectProjects(candidates, setOf(module1))

    // Module 1 explicitly requested, Module 2 has no artifact so must be included
    assertEquals(setOf(":module:1", ":module:2"), result.selectedProjects.map { it.path }.toSet())
    assertEquals(1, result.metrics.selectedDueToExplicitRequest)
    assertEquals(0, result.metrics.selectedDueToLocalChanges)
    assertEquals(1, result.metrics.selectedDueToMissingArtifact)
  }

  @Test
  fun `projects with artifact and no changes are excluded`() = runTest {
    val selector =
      RealArtifactSwapModuleSelector(
        localArtifactRepository = fakeLocalArtifactRepository,
        squareGit = fakeSquareGit,
        artifactSwapBomHelper = fakeBomHelper,
        ioDispatcher = Dispatchers.Unconfined,
        eventstream = fakeEventstream,
      )

    val module1 = GradlePath(rootDir, ":module:1")
    val module2 = GradlePath(rootDir, ":module:2")
    val candidates = setOf(module1, module2)

    // Both modules have artifacts, no changes, only module 1 requested
    fakeLocalArtifactRepository.installedArtifacts =
      setOf(
        InstalledArtifact(":module:1", setOf("abc123")),
        InstalledArtifact(":module:2", setOf("def456")),
      )
    fakeSquareGit.changedFiles = emptySet()

    val result = selector.selectProjects(candidates, setOf(module1))

    // Only module 1 (explicitly requested) should be included
    assertEquals(setOf(":module:1"), result.selectedProjects.map { it.path }.toSet())
    assertEquals(1, result.metrics.selectedDueToExplicitRequest)
    assertEquals(0, result.metrics.selectedDueToLocalChanges)
    assertEquals(0, result.metrics.selectedDueToMissingArtifact)
    assertEquals(1, result.metrics.excludedDueToArtifactAvailable)
    assertEquals("test-bom-version", result.bomVersion)
  }
}
