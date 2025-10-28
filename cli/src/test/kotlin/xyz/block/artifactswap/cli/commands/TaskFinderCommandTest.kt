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
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readLines
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the CLI command that verify picocli argument parsing
 * and proper wiring with the core TaskFinderService.
 */
class TaskFinderCommandTest {

    @TempDir
    lateinit var tempDir: File

    @TempDir
    lateinit var taskOutputDir: File

    private lateinit var fakeEventStream: FakeTaskFinderEventStream
    private lateinit var commandLine: CommandLine

    @BeforeEach
    fun setUp() {
        fakeEventStream = FakeTaskFinderEventStream()
        commandLine = CommandLine(TaskFinderCommand())
    }

    @Test
    fun `GIVEN valid CLI args WHEN executing THEN command parses args and finds tasks`() = runTest {
        // Create a simple Gradle project with a custom task
        val taskName = "customTask"
        val projectNames = listOf(":project1", ":project2")
        createGradleProject(tempDir.toPath(), projectNames, taskName)

        val command = commandLine.parseArgs(
            "--dir=${tempDir.absolutePath}",
            "--task=$taskName",
            "--task-list-output-directory=${taskOutputDir.absolutePath}",
            "--output-mode=SINGLE_TASK_LIST",
            "--build_id=build-123"
        ).commandSpec().commandLine().getCommand<TaskFinderCommand>()

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
            }
        )
        command.init(testApplication)
        testApplication.modules(
            module {
                single<xyz.block.artifactswap.core.task_finder.services.TaskFinderEventStream> { fakeEventStream }
            }
        )

        command.executeCommand(testApplication)

        // Verify that the tasks were found
        assertEquals(1, fakeEventStream.receivedResults.size)
        val result = fakeEventStream.receivedResults.first()
        assertEquals(2, result.countTasksWithName)

        // Verify output file was created with correct content
        val outputFiles = taskOutputDir.toPath().walk().filter { it.toFile().isFile }.toList()
        assertEquals(1, outputFiles.size)
        val lines = outputFiles.first().readLines()
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.endsWith(taskName) })
    }

    @Test
    fun `GIVEN SHARD_TASK_LIST mode WHEN executing THEN splits tasks into multiple files`() = runTest {
        // Create multiple projects
        val taskName = "publishTask"
        val projectNames = (1..10).map { ":project$it" }
        createGradleProject(tempDir.toPath(), projectNames, taskName)

        val command = commandLine.parseArgs(
            "--dir=${tempDir.absolutePath}",
            "--task=$taskName",
            "--task-list-output-directory=${taskOutputDir.absolutePath}",
            "--output-mode=SHARD_TASK_LIST",
            "--pages=3",
            "--chunks=3"
        ).commandSpec().commandLine().getCommand<TaskFinderCommand>()

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
            }
        )
        command.init(testApplication)
        testApplication.modules(
            module {
                single<xyz.block.artifactswap.core.task_finder.services.TaskFinderEventStream> { fakeEventStream }
            }
        )

        command.executeCommand(testApplication)

        // Verify that the tasks were found
        val result = fakeEventStream.receivedResults.first()
        assertEquals(10, result.countTasksWithName)

        // Verify multiple output files were created
        val outputFiles = taskOutputDir.toPath().walk()
            .filter { it.toFile().isFile }
            .sortedBy { it.fileName.toString() }
            .toList()
        assertTrue(outputFiles.size > 1, "Should have multiple shard files")

        // Verify all tasks are present across all files
        val allTasks = outputFiles.flatMap { it.readLines() }
        assertEquals(10, allTasks.size)
        assertTrue(allTasks.all { it.endsWith(taskName) })
    }

    @Test
    fun `GIVEN SINGLE_TASK_LIST mode WHEN executing THEN creates single output file`() = runTest {
        // Create multiple projects
        val taskName = "testTask"
        val projectNames = (1..5).map { ":project$it" }
        createGradleProject(tempDir.toPath(), projectNames, taskName)

        val command = commandLine.parseArgs(
            "--dir=${tempDir.absolutePath}",
            "--task=$taskName",
            "--task-list-output-directory=${taskOutputDir.absolutePath}",
            "--output-mode=SINGLE_TASK_LIST"
        ).commandSpec().commandLine().getCommand<TaskFinderCommand>()

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
            }
        )
        command.init(testApplication)
        testApplication.modules(
            module {
                single<xyz.block.artifactswap.core.task_finder.services.TaskFinderEventStream> { fakeEventStream }
            }
        )

        command.executeCommand(testApplication)

        // Verify single output file
        val outputFiles = taskOutputDir.toPath().walk().filter { it.toFile().isFile }.toList()
        assertEquals(1, outputFiles.size)
        assertEquals("task-output.out", outputFiles.first().fileName.toString())

        // Verify all tasks in single file
        val lines = outputFiles.first().readLines()
        assertEquals(5, lines.size)
        assertTrue(lines.all { it.endsWith(taskName) })
    }

    private fun createGradleProject(
        root: Path,
        projects: List<String>,
        taskName: String
    ) {
        projects.forEach { projectName ->
            createModule(root, projectName, """
                plugins {
                    id 'java'
                }
                tasks.register('$taskName')
            """.trimIndent())
        }

        val settingsContent = buildString {
            appendLine("rootProject.name = 'test'")
            projects.forEach { projectName ->
                appendLine("include '$projectName'")
            }
        }
        createSettingsFile(root, settingsContent)
    }

    private fun createModule(
        rootDir: Path,
        name: String,
        contents: String
    ): Path {
        val pathDir = name.removePrefix(":").replace(':', File.separatorChar)
        return rootDir.resolve(pathDir).createDirectories().apply {
            resolve("build.gradle").apply {
                writeText(contents)
            }
        }
    }

    private fun createSettingsFile(
        rootDir: Path,
        contents: String
    ) {
        rootDir.resolve("settings.gradle").apply {
            writeText(contents)
        }
    }
}
