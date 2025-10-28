package xyz.block.artifactswap.cli.commands

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.mockito.kotlin.mock
import picocli.CommandLine
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.eventstream.EventstreamService
import xyz.block.artifactswap.core.task_runner.models.TaskRunnerResult
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the CLI command that verify picocli argument parsing
 * and proper wiring with the core TaskRunnerService.
 */
class TaskRunnerCommandTest {

    @TempDir
    lateinit var tempDir: File

    @TempDir
    lateinit var taskListDir: File

    private lateinit var fakeEventStream: FakeTaskRunnerEventStream
    private lateinit var commandLine: CommandLine

    @BeforeEach
    fun setUp() {
        fakeEventStream = FakeTaskRunnerEventStream()
        commandLine = CommandLine(TaskRunnerCommand())
    }

    @Test
    fun `GIVEN valid task list file WHEN executing THEN command parses args and runs tasks`() = runTest {
        // Create a simple Gradle project
        createGradleProject(tempDir.toPath())

        // Create task list file
        val taskListFile = taskListDir.toPath().resolve("tasks.txt")
        taskListFile.writeText(":help")

        val command = commandLine.parseArgs(
            "--dir=${tempDir.absolutePath}",
            "--task-list-file=${taskListFile}",
            "--build_id=build-123"
        ).commandSpec().commandLine().getCommand<TaskRunnerCommand>()

        val testApplication = koinApplication {
            allowOverride(true)
        }
        testApplication.modules(
            module {
                single(named("IO")) { kotlinx.coroutines.Dispatchers.Unconfined }
                single(named("Default")) { kotlinx.coroutines.Dispatchers.Unconfined }
                single(named("directory")) { Path(tempDir.absolutePath) }
                single<Eventstream>(named("analyticsModuleEventStream")) {
                    Eventstream(eventstreamService = mock<EventstreamService>())
                }
                single(named("gradleArgs")) { emptyList<String>() }
                single(named("gradleJvmArgs")) { emptyList<String>() }
                single(named("dryRun")) { false }
            }
        )
        command.init(testApplication)
        testApplication.modules(
            module {
                single<xyz.block.artifactswap.core.task_runner.services.TaskRunnerEventStream> { fakeEventStream }
            }
        )

        command.executeCommand(testApplication)

        // Verify that the tasks were executed
        assertEquals(1, fakeEventStream.receivedResults.size)
        val result = fakeEventStream.receivedResults.first()
        assertEquals(TaskRunnerResult.SUCCESS, result.result)
        assertEquals(1, result.countTasksToRun)
    }

    @Test
    fun `GIVEN empty task list file WHEN executing THEN returns NO_OP result`() = runTest {
        // Create a simple Gradle project
        createGradleProject(tempDir.toPath())

        // Create empty task list file
        val taskListFile = taskListDir.toPath().resolve("empty-tasks.txt")
        taskListFile.writeText("")

        val command = commandLine.parseArgs(
            "--dir=${tempDir.absolutePath}",
            "--task-list-file=${taskListFile}"
        ).commandSpec().commandLine().getCommand<TaskRunnerCommand>()

        val testApplication = koinApplication {
            allowOverride(true)
        }
        testApplication.modules(
            module {
                single(named("IO")) { kotlinx.coroutines.Dispatchers.Unconfined }
                single(named("Default")) { kotlinx.coroutines.Dispatchers.Unconfined }
                single(named("directory")) { Path(tempDir.absolutePath) }
                single<Eventstream>(named("analyticsModuleEventStream")) {
                    Eventstream(eventstreamService = mock<EventstreamService>())
                }
                single(named("gradleArgs")) { emptyList<String>() }
                single(named("gradleJvmArgs")) { emptyList<String>() }
                single(named("dryRun")) { false }
            }
        )
        command.init(testApplication)
        testApplication.modules(
            module {
                single<xyz.block.artifactswap.core.task_runner.services.TaskRunnerEventStream> { fakeEventStream }
            }
        )

        command.executeCommand(testApplication)

        // Verify NO_OP result for empty task list
        assertEquals(1, fakeEventStream.receivedResults.size)
        val result = fakeEventStream.receivedResults.first()
        assertEquals(TaskRunnerResult.NO_OP, result.result)
        assertEquals(0, result.countTasksToRun)
    }

    @Test
    fun `GIVEN multiple tasks WHEN executing THEN runs all tasks and tracks results`() = runTest {
        // Create a simple Gradle project
        createGradleProject(tempDir.toPath())

        // Create task list file with multiple tasks
        val taskListFile = taskListDir.toPath().resolve("multiple-tasks.txt")
        taskListFile.writeText(":help\n:tasks")

        val command = commandLine.parseArgs(
            "--dir=${tempDir.absolutePath}",
            "--task-list-file=${taskListFile}"
        ).commandSpec().commandLine().getCommand<TaskRunnerCommand>()

        val testApplication = koinApplication {
            allowOverride(true)
        }
        testApplication.modules(
            module {
                single(named("IO")) { kotlinx.coroutines.Dispatchers.Unconfined }
                single(named("Default")) { kotlinx.coroutines.Dispatchers.Unconfined }
                single(named("directory")) { Path(tempDir.absolutePath) }
                single<Eventstream>(named("analyticsModuleEventStream")) {
                    Eventstream(eventstreamService = mock<EventstreamService>())
                }
                single(named("gradleArgs")) { emptyList<String>() }
                single(named("gradleJvmArgs")) { emptyList<String>() }
                single(named("dryRun")) { false }
            }
        )
        command.init(testApplication)
        testApplication.modules(
            module {
                single<xyz.block.artifactswap.core.task_runner.services.TaskRunnerEventStream> { fakeEventStream }
            }
        )

        command.executeCommand(testApplication)

        // Verify multiple tasks were tracked
        val result = fakeEventStream.receivedResults.first()
        assertEquals(TaskRunnerResult.SUCCESS, result.result)
        assertEquals(2, result.countTasksToRun)
        assertTrue(result.countTasksSucceeded >= 0)
    }

    private fun createGradleProject(root: Path) {
        // Create a minimal build.gradle
        root.resolve("build.gradle").writeText("""
            plugins {
                id 'java'
            }
        """.trimIndent())

        // Create settings.gradle
        root.resolve("settings.gradle").writeText("""
            rootProject.name = 'test'
        """.trimIndent())
    }
}
