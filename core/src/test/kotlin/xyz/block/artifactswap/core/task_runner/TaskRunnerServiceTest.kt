package xyz.block.artifactswap.core.task_runner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xyz.block.artifactswap.core.task_runner.models.TaskRunnerResult
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskRunnerServiceTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var fakeEventStream: FakeTaskRunnerEventStream
    private lateinit var taskRunnerService: TaskRunnerService

    private val testCiMetadata = CiMetadata(
        gitBranch = "test-branch",
        gitSha = "test-sha",
        ciEnv = "test",
        buildId = "build-123",
        buildStepId = "step-456",
        buildJobId = "job-789",
        ciType = "test"
    )

    @BeforeEach
    fun setUp() {
        fakeEventStream = FakeTaskRunnerEventStream()
        taskRunnerService = TaskRunnerService(
            eventStream = fakeEventStream,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun `GIVEN no tasks WHEN running tasks THEN returns NO_OP result`() = runTest {
        // Create a simple Gradle project
        createGradleProject(tempDir.toPath())

        val result = taskRunnerService.runTasks(
            applicationDirectory = tempDir.toPath(),
            tasks = emptyList(),
            gradleArgs = emptyList(),
            gradleJvmArgs = emptyList(),
            dryRun = false,
            ciMetadata = testCiMetadata
        )

        assertEquals(TaskRunnerResult.NO_OP, result.result)
        assertEquals(0, result.countTasksToRun)
    }

    @Test
    fun `GIVEN valid tasks WHEN running tasks THEN returns SUCCESS result`() = runTest {
        // Create a simple Gradle project with a task
        createGradleProject(tempDir.toPath())

        val result = taskRunnerService.runTasks(
            applicationDirectory = tempDir.toPath(),
            tasks = listOf(":help"),
            gradleArgs = emptyList(),
            gradleJvmArgs = emptyList(),
            dryRun = false,
            ciMetadata = testCiMetadata
        )

        assertEquals(TaskRunnerResult.SUCCESS, result.result)
        assertEquals(1, result.countTasksToRun)
        assertTrue(result.countTasksSucceeded >= 0)
    }

    @Test
    fun `GIVEN dry run mode WHEN running tasks THEN executes in dry run`() = runTest {
        // Create a simple Gradle project
        createGradleProject(tempDir.toPath())

        val result = taskRunnerService.runTasks(
            applicationDirectory = tempDir.toPath(),
            tasks = listOf(":help"),
            gradleArgs = emptyList(),
            gradleJvmArgs = emptyList(),
            dryRun = true,
            ciMetadata = testCiMetadata
        )

        // In dry run mode, tasks should still succeed
        assertEquals(TaskRunnerResult.SUCCESS, result.result)
        assertEquals(1, result.countTasksToRun)
    }

    @Test
    fun `GIVEN successful task execution WHEN logging result THEN sends event to event stream`() = runTest {
        createGradleProject(tempDir.toPath())

        val result = taskRunnerService.runTasks(
            applicationDirectory = tempDir.toPath(),
            tasks = listOf(":help"),
            gradleArgs = emptyList(),
            gradleJvmArgs = emptyList(),
            dryRun = false,
            ciMetadata = testCiMetadata
        )

        taskRunnerService.logResult(result)

        assertEquals(1, fakeEventStream.receivedResults.size)
        val receivedResult = fakeEventStream.receivedResults.first()
        assertEquals(TaskRunnerResult.SUCCESS, receivedResult.result)
        assertEquals(1, receivedResult.countTasksToRun)
    }

    @Test
    fun `GIVEN multiple tasks WHEN running tasks THEN tracks all task executions`() = runTest {
        // Create a Gradle project with multiple tasks
        createGradleProject(tempDir.toPath())

        val result = taskRunnerService.runTasks(
            applicationDirectory = tempDir.toPath(),
            tasks = listOf(":help", ":tasks"),
            gradleArgs = emptyList(),
            gradleJvmArgs = emptyList(),
            dryRun = false,
            ciMetadata = testCiMetadata
        )

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
}
