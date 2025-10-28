package xyz.block.artifactswap.core.task_finder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xyz.block.artifactswap.core.task_finder.models.TaskFinderResult
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskFinderServiceTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var fakeEventStream: FakeTaskFinderEventStream
    private lateinit var taskFinderService: TaskFinderService

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
        fakeEventStream = FakeTaskFinderEventStream()
        taskFinderService = TaskFinderService(
            eventStream = fakeEventStream,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun `GIVEN project with tasks WHEN finding tasks THEN returns matching tasks`() = runTest {
        // Create a simple Gradle project with a custom task
        val taskName = "customTask"
        createGradleProject(
            root = tempDir.toPath(),
            projects = listOf(":project1"),
            taskName = taskName
        )

        val result = taskFinderService.findTasks(
            applicationDirectory = tempDir.toPath(),
            taskName = taskName,
            gradleArgs = emptyList(),
            gradleJvmArgs = emptyList(),
            ciMetadata = testCiMetadata
        )

        assertEquals(TaskFinderResult.SUCCESS, result.serviceResult.result)
        assertEquals(1, result.serviceResult.countTasksWithName)
        assertEquals(1, result.tasks.size)
        assertTrue(result.tasks.first().path.endsWith(taskName))
    }

    @Test
    fun `GIVEN multiple projects WHEN finding tasks THEN returns all matching tasks`() = runTest {
        // Create multiple projects with the same task
        val taskName = "publishTask"
        val projectNames = listOf(":project1", ":project2", ":project3")
        createGradleProject(
            root = tempDir.toPath(),
            projects = projectNames,
            taskName = taskName
        )

        val result = taskFinderService.findTasks(
            applicationDirectory = tempDir.toPath(),
            taskName = taskName,
            gradleArgs = emptyList(),
            gradleJvmArgs = emptyList(),
            ciMetadata = testCiMetadata
        )

        assertEquals(TaskFinderResult.SUCCESS, result.serviceResult.result)
        assertEquals(3, result.serviceResult.countTasksWithName)
        assertEquals(3, result.tasks.size)
        assertTrue(result.tasks.all { it.path.endsWith(taskName) })
    }

    @Test
    fun `GIVEN projects with subset having task WHEN finding tasks THEN returns only matching tasks`() = runTest {
        // Create projects where only some have the task
        val taskName = "specificTask"
        val root = tempDir.toPath()

        // Project 1 has the task
        createModule(root, ":project1", """
            plugins {
                id 'java'
            }
            tasks.register('$taskName')
        """.trimIndent())

        // Project 2 does not have the task
        createModule(root, ":project2", """
            plugins {
                id 'java'
            }
        """.trimIndent())

        // Project 3 has the task
        createModule(root, ":project3", """
            plugins {
                id 'java'
            }
            tasks.register('$taskName')
        """.trimIndent())

        createSettingsFile(root, """
            rootProject.name = 'test'
            include ':project1'
            include ':project2'
            include ':project3'
        """.trimIndent())

        val result = taskFinderService.findTasks(
            applicationDirectory = root,
            taskName = taskName,
            gradleArgs = emptyList(),
            gradleJvmArgs = emptyList(),
            ciMetadata = testCiMetadata
        )

        assertEquals(TaskFinderResult.SUCCESS, result.serviceResult.result)
        assertEquals(2, result.serviceResult.countTasksWithName)
        assertEquals(2, result.tasks.size)
    }

    @Test
    fun `GIVEN project with no matching tasks WHEN finding tasks THEN returns empty list`() = runTest {
        // Create a project without the task we're looking for
        val taskName = "nonExistentTask"
        createGradleProject(
            root = tempDir.toPath(),
            projects = listOf(":project1"),
            taskName = "differentTask"
        )

        val result = taskFinderService.findTasks(
            applicationDirectory = tempDir.toPath(),
            taskName = taskName,
            gradleArgs = emptyList(),
            gradleJvmArgs = emptyList(),
            ciMetadata = testCiMetadata
        )

        assertEquals(TaskFinderResult.SUCCESS, result.serviceResult.result)
        assertEquals(0, result.serviceResult.countTasksWithName)
        assertEquals(0, result.tasks.size)
    }

    @Test
    fun `GIVEN successful task finding WHEN logging result THEN sends event to event stream`() = runTest {
        val taskName = "testTask"
        createGradleProject(
            root = tempDir.toPath(),
            projects = listOf(":project1"),
            taskName = taskName
        )

        val result = taskFinderService.findTasks(
            applicationDirectory = tempDir.toPath(),
            taskName = taskName,
            gradleArgs = emptyList(),
            gradleJvmArgs = emptyList(),
            ciMetadata = testCiMetadata
        )

        taskFinderService.logResult(result.serviceResult)

        assertEquals(1, fakeEventStream.receivedResults.size)
        val receivedResult = fakeEventStream.receivedResults.first()
        assertEquals(TaskFinderResult.SUCCESS, receivedResult.result)
        assertEquals(1, receivedResult.countTasksWithName)
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
