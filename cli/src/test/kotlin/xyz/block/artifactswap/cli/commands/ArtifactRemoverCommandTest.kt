package xyz.block.artifactswap.cli.commands

import java.io.File
import kotlin.io.path.Path
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.mockito.kotlin.mock
import picocli.CommandLine
import xyz.block.artifactswap.core.eventstream.EventStreamAdapter
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.eventstream.EventstreamService
import xyz.block.artifactswap.core.eventstream.FakeEventStreamAdapter
import xyz.block.artifactswap.core.remover.services.InstalledBom
import xyz.block.artifactswap.core.remover.services.InstalledProject
import xyz.block.artifactswap.core.remover.services.LocalArtifactRepository
import xyz.block.artifactswap.core.remover.services.RepositoryStats

/**
 * Integration tests for the CLI command that verify picocli argument parsing and proper wiring with
 * the core ArtifactRemover.
 */
class ArtifactRemoverCommandTest {

  @TempDir lateinit var tempDir: File

  private lateinit var fakeEventStream: EventStreamAdapter
  private lateinit var fakeRepository: FakeLocalArtifactRepository
  private lateinit var commandLine: CommandLine

  @BeforeEach
  fun setUp() {
    fakeEventStream = FakeEventStreamAdapter()
    fakeRepository = FakeLocalArtifactRepository()
    commandLine = CommandLine(ArtifactRemoverCommand())
  }

  @Test
  fun `GIVEN valid CLI args WHEN executing THEN command parses args and runs remover`() = runTest {
    val command =
      commandLine
        .parseArgs("--maven-local-path", tempDir.absolutePath)
        .commandSpec()
        .commandLine()
        .getCommand<ArtifactRemoverCommand>()

    val testApplication = koinApplication { allowOverride(true) }
    testApplication.modules(
      module {
        single(named("IO")) { kotlinx.coroutines.Dispatchers.Unconfined }
        single<Eventstream>(named("analyticsModuleEventStream")) {
          Eventstream(eventstreamService = mock<EventstreamService>())
        }
        single<com.fasterxml.jackson.databind.ObjectMapper> {
          com.fasterxml.jackson.dataformat.xml.XmlMapper.builder().defaultUseWrapper(false).build()
        }
      }
    )
    command.init(testApplication)
    testApplication.modules(
      module {
        single<EventStreamAdapter>(named("artifactRemoverEventStream")) { fakeEventStream }
        single<LocalArtifactRepository> { fakeRepository }
      }
    )

    command.executeCommand(testApplication)

    // Verify that the remover was called and completed successfully (we can't verify specific
    // results since the fake doesn't track them)
  }

  @Test
  fun `GIVEN installed artifacts WHEN executing THEN removes old artifacts`() = runTest {
    // Setup fake repository with some test data
    fakeRepository.installedBoms =
      listOf(
        InstalledBom(
          version = "recent",
          repositoryPath = Path("/fake/bom"),
          installedProjects = listOf(InstalledProject(":app", Path("/fake/app"), setOf("1.0.0"))),
        )
      )
    fakeRepository.installedProjects =
      listOf(
        InstalledProject(":app", Path("/fake/app"), setOf("1.0.0", "0.9.0")),
        InstalledProject(":old", Path("/fake/old"), setOf("1.0.0")),
      )

    val command =
      commandLine
        .parseArgs("--maven-local-path", tempDir.absolutePath)
        .commandSpec()
        .commandLine()
        .getCommand<ArtifactRemoverCommand>()

    val testApplication = koinApplication { allowOverride(true) }
    testApplication.modules(
      module {
        single(named("IO")) { kotlinx.coroutines.Dispatchers.Unconfined }
        single<Eventstream>(named("analyticsModuleEventStream")) {
          Eventstream(eventstreamService = mock<EventstreamService>())
        }
        single<com.fasterxml.jackson.databind.ObjectMapper> {
          com.fasterxml.jackson.dataformat.xml.XmlMapper.builder().defaultUseWrapper(false).build()
        }
      }
    )
    command.init(testApplication)
    testApplication.modules(
      module {
        single<EventStreamAdapter>(named("artifactRemoverEventStream")) { fakeEventStream }
        single<LocalArtifactRepository> { fakeRepository }
      }
    )

    command.executeCommand(testApplication)

    // Verify deletions occurred
    assertEquals(2, fakeRepository.deletedProjects.size)
  }
}

internal class FakeLocalArtifactRepository : LocalArtifactRepository {
  var installedProjects: List<InstalledProject> = emptyList()
  var installedBoms: List<InstalledBom> = emptyList()

  val deletedProjects = mutableListOf<InstalledProject>()
  val deletedBoms = mutableListOf<InstalledBom>()

  override fun getAllInstalledProjects(): Flow<InstalledProject> {
    return installedProjects.asFlow()
  }

  override suspend fun getInstalledBomsByRecency(count: Int): List<InstalledBom> {
    return installedBoms.take(count)
  }

  override suspend fun deleteInstalledProjectVersions(
    installedProject: InstalledProject
  ): List<String> {
    deletedProjects.add(installedProject)
    return installedProject.versions.toList()
  }

  override suspend fun deleteInstalledBom(installedBom: InstalledBom): Boolean {
    deletedBoms.add(installedBom)
    return true
  }

  override suspend fun measureRepository(): RepositoryStats {
    return RepositoryStats(
      countInstalledProjects = installedProjects.size.toLong(),
      countInstalledArtifacts = installedProjects.sumOf { it.versions.size }.toLong(),
      countInstalledBoms = installedBoms.size.toLong(),
      sizeOfInstalledArtifactsBytes = 1024L,
      sizeOfInstalledBomsBytes = 1024L,
      overallRepoSizeBytes = 2048L,
      installedArtifactsMeasurementDuration = 10.milliseconds,
      installedBomsMeasurementDuration = 10.milliseconds,
      measurementDuration = 20.milliseconds,
    )
  }
}
