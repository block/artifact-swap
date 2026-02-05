package xyz.block.artifactswap.cli.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import java.io.File
import kotlin.io.path.Path
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.mockito.kotlin.mock
import picocli.CommandLine
import xyz.block.artifactswap.core.config.ArtifactSwapConfig
import xyz.block.artifactswap.core.config.testArtifactSwapConfig
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.eventstream.EventstreamService
import xyz.block.artifactswap.core.remover.models.ArtifactRemoverEventResult
import xyz.block.artifactswap.core.remover.services.ArtifactRemoverEventStream
import xyz.block.artifactswap.core.repository.FakeLocalArtifactRepository
import xyz.block.artifactswap.core.repository.InstalledBom
import xyz.block.artifactswap.core.repository.InstalledProject
import xyz.block.artifactswap.core.repository.LocalArtifactRepository

/**
 * Integration tests for the CLI command that verify picocli argument parsing and proper wiring with
 * the core ArtifactRemover.
 */
class ArtifactRemoverCommandTest {

  @TempDir lateinit var tempDir: File

  private lateinit var fakeEventStream: FakeArtifactRemoverEventStream
  private lateinit var fakeRepository: FakeLocalArtifactRepository
  private lateinit var commandLine: CommandLine

  @BeforeEach
  fun setUp() {
    fakeEventStream = FakeArtifactRemoverEventStream()
    fakeRepository = FakeLocalArtifactRepository()
    commandLine = CommandLine(ArtifactRemoverCommand())
  }

  @Test
  fun `GIVEN valid CLI args WHEN executing THEN command parses args and runs remover`() = runTest {
    val command =
      commandLine.parseArgs().commandSpec().commandLine().getCommand<ArtifactRemoverCommand>()

    val testApplication = koinApplication { allowOverride(true) }
    testApplication.modules(
      module {
        single(named("IO")) { Dispatchers.Unconfined }
        single<Eventstream>() { Eventstream(eventstreamService = mock<EventstreamService>()) }
        single<ObjectMapper> { XmlMapper.builder().defaultUseWrapper(false).build() }
        single<ArtifactSwapConfig> {
          testArtifactSwapConfig(mavenLocalDirectory = tempDir.absolutePath)
        }
      }
    )
    command.init(testApplication)
    testApplication.modules(
      module {
        single<ArtifactRemoverEventStream> { fakeEventStream }
        single<LocalArtifactRepository> { fakeRepository }
      }
    )

    command.executeCommand(testApplication)

    // Verify that the remover was called and completed successfully
    assertEquals(1, fakeEventStream.receivedResults.size)
    val result = fakeEventStream.receivedResults.first()
    assertEquals(ArtifactRemoverEventResult.SUCCESS, result.result)
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
      commandLine.parseArgs().commandSpec().commandLine().getCommand<ArtifactRemoverCommand>()

    val testApplication = koinApplication { allowOverride(true) }
    testApplication.modules(
      module {
        single(named("IO")) { Dispatchers.Unconfined }
        single<Eventstream>() { Eventstream(eventstreamService = mock<EventstreamService>()) }
        single<ObjectMapper> { XmlMapper.builder().defaultUseWrapper(false).build() }
        single<ArtifactSwapConfig> {
          testArtifactSwapConfig(mavenLocalDirectory = tempDir.absolutePath)
        }
      }
    )
    command.init(testApplication)
    testApplication.modules(
      module {
        single<ArtifactRemoverEventStream> { fakeEventStream }
        single<LocalArtifactRepository> { fakeRepository }
      }
    )

    command.executeCommand(testApplication)

    // Verify deletions occurred
    assertEquals(2, fakeRepository.deletedProjects.size)
    assertEquals(1, fakeEventStream.receivedResults.size)
    assertEquals(ArtifactRemoverEventResult.SUCCESS, fakeEventStream.receivedResults.first().result)
  }
}

// Test fakes for CLI tests
internal class FakeArtifactRemoverEventStream : ArtifactRemoverEventStream {
  val receivedResults =
    mutableListOf<xyz.block.artifactswap.core.remover.models.ArtifactRemoverResult>()

  override suspend fun sendResults(
    results: List<xyz.block.artifactswap.core.remover.models.ArtifactRemoverResult>
  ): Boolean {
    receivedResults.addAll(results)
    return true
  }
}
