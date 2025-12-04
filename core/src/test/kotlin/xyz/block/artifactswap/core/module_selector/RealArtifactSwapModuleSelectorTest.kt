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
import xyz.block.artifactswap.core.repository.FakeLocalArtifactRepository
import xyz.block.artifactswap.core.repository.InstalledArtifact

class RealArtifactSwapModuleSelectorTest {

  @TempDir lateinit var rootDir: Path

  private val fakeLocalArtifactRepository = FakeLocalArtifactRepository()
  private val fakeSquareGit = FakeSquareGit()
  private val fakeBomHelper = FakeArtifactSwapBomLoader()
  private val fakeEventstream = mock<Eventstream>()

  private fun setupModulesWithDependency(
    module1Path: String,
    module2Path: String,
  ): Pair<GradlePath, GradlePath> {
    val module1 = GradlePath(rootDir, module1Path)
    val module2 = GradlePath(rootDir, module2Path)

    // Create directory structure for module1
    val dirPath1 = module1Path.substring(1).replace(':', '/')
    rootDir.resolve(dirPath1).toFile().mkdirs()

    // Create directory structure for module2
    val dirPath2 = module2Path.substring(1).replace(':', '/')
    rootDir.resolve(dirPath2).toFile().mkdirs()

    // Create build.gradle for module1 with dependency on module2
    rootDir
      .resolve("$dirPath1/build.gradle")
      .toFile()
      .writeText(
        """
      dependencies {
        implementation(project("$module2Path"))
      }
    """
          .trimIndent()
      )

    // Create empty build.gradle for module2
    rootDir.resolve("$dirPath2/build.gradle").toFile().writeText("")

    return Pair(module1, module2)
  }

  @Test
  fun `explicitly requested projects are always included`() = runTest {
    val (module1, module2) = setupModulesWithDependency(":module:1", ":module:2")

    val selector =
      RealArtifactSwapModuleSelector(
        localArtifactRepository = fakeLocalArtifactRepository,
        squareGit = fakeSquareGit,
        bomLoader = fakeBomHelper,
        ioDispatcher = Dispatchers.Unconfined,
        eventstream = fakeEventstream,
        spotlightRules = emptySet(), // No rules needed - we'll mock the candidates
      )

    // Both modules have artifacts and no changes, but module 1 is explicitly requested
    fakeLocalArtifactRepository.installedArtifacts =
      setOf(
        InstalledArtifact(":module:1", setOf("abc123")),
        InstalledArtifact(":module:2", setOf("def456")),
      )
    fakeSquareGit.changedFiles = emptySet()

    // Request both modules so they're both candidates, but only module1 is "explicitly requested"
    // (this tests that module2 gets excluded even though it's a candidate)
    val result = selector.selectProjects(setOf(module1))

    assertEquals(setOf(":module:1"), result.selectedProjects.map { it.path }.toSet())
    assertEquals(1, result.metrics.selectedDueToExplicitRequest)
    assertEquals(0, result.metrics.selectedDueToLocalChanges)
    assertEquals(0, result.metrics.selectedDueToMissingArtifact)
    assertEquals(1, result.metrics.excludedDueToArtifactAvailable)
    assertEquals("test-bom-version", result.bomVersionToUse)
  }

  @Test
  fun `projects with local changes are included`() = runTest {
    val (module1, module2) = setupModulesWithDependency(":module:1", ":module:2")

    val selector =
      RealArtifactSwapModuleSelector(
        localArtifactRepository = fakeLocalArtifactRepository,
        squareGit = fakeSquareGit,
        bomLoader = fakeBomHelper,
        ioDispatcher = Dispatchers.Unconfined,
        eventstream = fakeEventstream,
        spotlightRules = emptySet(),
      )

    // Both modules have artifacts, but module 2 has local changes
    fakeLocalArtifactRepository.installedArtifacts =
      setOf(
        InstalledArtifact(":module:1", setOf("abc123")),
        InstalledArtifact(":module:2", setOf("def456")),
      )
    fakeSquareGit.changedFiles = setOf(rootDir.resolve("module/2/SomeFile.kt"))

    val result = selector.selectProjects(setOf(module1))

    // Module 1 explicitly requested, Module 2 has changes
    assertEquals(setOf(":module:1", ":module:2"), result.selectedProjects.map { it.path }.toSet())
    assertEquals(1, result.metrics.selectedDueToExplicitRequest)
    assertEquals(1, result.metrics.selectedDueToLocalChanges)
    assertEquals(0, result.metrics.selectedDueToMissingArtifact)
  }

  @Test
  fun `projects without local artifacts are included`() = runTest {
    val (module1, module2) = setupModulesWithDependency(":module:1", ":module:2")

    val selector =
      RealArtifactSwapModuleSelector(
        localArtifactRepository = fakeLocalArtifactRepository,
        squareGit = fakeSquareGit,
        bomLoader = fakeBomHelper,
        ioDispatcher = Dispatchers.Unconfined,
        eventstream = fakeEventstream,
        spotlightRules = emptySet(),
      )

    // Only module 1 has an artifact
    fakeLocalArtifactRepository.installedArtifacts =
      setOf(InstalledArtifact(":module:1", setOf("abc123")))
    fakeSquareGit.changedFiles = emptySet()

    val result = selector.selectProjects(setOf(module1))

    // Module 1 explicitly requested, Module 2 has no artifact so must be included
    assertEquals(setOf(":module:1", ":module:2"), result.selectedProjects.map { it.path }.toSet())
    assertEquals(1, result.metrics.selectedDueToExplicitRequest)
    assertEquals(0, result.metrics.selectedDueToLocalChanges)
    assertEquals(1, result.metrics.selectedDueToMissingArtifact)
  }

  @Test
  fun `projects with artifact and no changes are excluded`() = runTest {
    val (module1, module2) = setupModulesWithDependency(":module:1", ":module:2")

    val selector =
      RealArtifactSwapModuleSelector(
        localArtifactRepository = fakeLocalArtifactRepository,
        squareGit = fakeSquareGit,
        bomLoader = fakeBomHelper,
        ioDispatcher = Dispatchers.Unconfined,
        eventstream = fakeEventstream,
        spotlightRules = emptySet(),
      )

    // Both modules have artifacts, no changes, only module 1 requested
    fakeLocalArtifactRepository.installedArtifacts =
      setOf(
        InstalledArtifact(":module:1", setOf("abc123")),
        InstalledArtifact(":module:2", setOf("def456")),
      )
    fakeSquareGit.changedFiles = emptySet()

    val result = selector.selectProjects(setOf(module1))

    // Only module 1 (explicitly requested) should be included
    assertEquals(setOf(":module:1"), result.selectedProjects.map { it.path }.toSet())
    assertEquals(1, result.metrics.selectedDueToExplicitRequest)
    assertEquals(0, result.metrics.selectedDueToLocalChanges)
    assertEquals(0, result.metrics.selectedDueToMissingArtifact)
    assertEquals(1, result.metrics.excludedDueToArtifactAvailable)
  }
}
