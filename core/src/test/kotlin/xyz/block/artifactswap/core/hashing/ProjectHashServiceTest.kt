package xyz.block.artifactswap.core.hashing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xyz.block.artifactswap.core.gradle.ProjectHashingInfo
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectHashServiceTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var fakeGradleProjectsProvider: FakeGradleProjectsProvider
    private lateinit var fakeEventStream: FakeHashingEventStream
    private lateinit var projectHashService: ProjectHashService

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
        fakeGradleProjectsProvider = FakeGradleProjectsProvider()
        fakeEventStream = FakeHashingEventStream()
        projectHashService = ProjectHashService(
            gradleProjectsProvider = fakeGradleProjectsProvider,
            eventStream = fakeEventStream,
            ioDispatcher = Dispatchers.Unconfined,
            defaultDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun `GIVEN no projects WHEN hashing THEN returns empty result`() = runTest {
        fakeGradleProjectsProvider.projectHashingInfos = Result.success(emptyList())

        val result = projectHashService.hashProjects(
            applicationDirectory = Path(tempDir.absolutePath),
            useBuildLogicVersion = false,
            useProjects = emptySet(),
            ciMetadata = testCiMetadata
        )

        assertEquals(0, result.countProjectsHashed)
        assertEquals(0, result.countFilesHashed)
        assertTrue(result.projectHashes.isEmpty())
        assertTrue(fakeGradleProjectsProvider.cleanupCalled)
    }

    @Test
    fun `GIVEN single project WHEN hashing THEN returns hash for project`() = runTest {
        val testFile = Path(tempDir.absolutePath, "test.txt")
        testFile.writeText("test content")

        val projectInfo = ProjectHashingInfo(
            projectPath = ":test-project",
            projectDirectory = Path(tempDir.absolutePath),
            filesToHash = sequenceOf(testFile)
        )

        fakeGradleProjectsProvider.projectHashingInfos = Result.success(listOf(projectInfo))

        val result = projectHashService.hashProjects(
            applicationDirectory = Path(tempDir.absolutePath),
            useBuildLogicVersion = false,
            useProjects = emptySet(),
            ciMetadata = testCiMetadata
        )

        assertEquals(1, result.countProjectsHashed)
        assertEquals(1, result.countFilesHashed)
        assertEquals(1, result.projectHashes.size)
        val hashResult = result.projectHashes.first()
        assertEquals(":test-project", hashResult.projectPath)
        assertTrue(hashResult.hash.isNotEmpty())
        assertEquals(1, hashResult.countFilesHashed)
    }

    @Test
    fun `GIVEN multiple projects WHEN hashing THEN returns hashes for all projects`() = runTest {
        val testFile1 = Path(tempDir.absolutePath, "test1.txt")
        testFile1.writeText("test content 1")
        val testFile2 = Path(tempDir.absolutePath, "test2.txt")
        testFile2.writeText("test content 2")

        val projectInfo1 = ProjectHashingInfo(
            projectPath = ":project1",
            projectDirectory = Path(tempDir.absolutePath),
            filesToHash = sequenceOf(testFile1)
        )
        val projectInfo2 = ProjectHashingInfo(
            projectPath = ":project2",
            projectDirectory = Path(tempDir.absolutePath),
            filesToHash = sequenceOf(testFile2)
        )

        fakeGradleProjectsProvider.projectHashingInfos = Result.success(listOf(projectInfo1, projectInfo2))

        val result = projectHashService.hashProjects(
            applicationDirectory = Path(tempDir.absolutePath),
            useBuildLogicVersion = false,
            useProjects = emptySet(),
            ciMetadata = testCiMetadata
        )

        assertEquals(2, result.countProjectsHashed)
        assertEquals(2, result.countFilesHashed)
        assertEquals(2, result.projectHashes.size)
        assertTrue(result.projectHashes.any { it.projectPath == ":project1" })
        assertTrue(result.projectHashes.any { it.projectPath == ":project2" })
    }

    @Test
    fun `GIVEN useProjects filter WHEN hashing THEN only hashes specified projects`() = runTest {
        val testFile1 = Path(tempDir.absolutePath, "test1.txt")
        testFile1.writeText("test content 1")
        val testFile2 = Path(tempDir.absolutePath, "test2.txt")
        testFile2.writeText("test content 2")

        val projectInfo1 = ProjectHashingInfo(
            projectPath = ":project1",
            projectDirectory = Path(tempDir.absolutePath),
            filesToHash = sequenceOf(testFile1)
        )
        val projectInfo2 = ProjectHashingInfo(
            projectPath = ":project2",
            projectDirectory = Path(tempDir.absolutePath),
            filesToHash = sequenceOf(testFile2)
        )

        fakeGradleProjectsProvider.projectHashingInfos = Result.success(listOf(projectInfo1, projectInfo2))

        val result = projectHashService.hashProjects(
            applicationDirectory = Path(tempDir.absolutePath),
            useBuildLogicVersion = false,
            useProjects = setOf(":project1"),
            ciMetadata = testCiMetadata
        )

        assertEquals(1, result.countProjectsHashed)
        assertEquals(1, result.countFilesHashed)
        assertEquals(1, result.projectHashes.size)
        assertEquals(":project1", result.projectHashes.first().projectPath)
    }

    @Test
    fun `GIVEN failed to get projects WHEN hashing THEN returns result with no projects`() = runTest {
        fakeGradleProjectsProvider.projectHashingInfos = Result.failure(Exception("Failed to get projects"))

        val result = projectHashService.hashProjects(
            applicationDirectory = Path(tempDir.absolutePath),
            useBuildLogicVersion = false,
            useProjects = emptySet(),
            ciMetadata = testCiMetadata
        )

        assertEquals(-1, result.countProjectsHashed)
        assertEquals(-1, result.countFilesHashed)
        assertTrue(result.projectHashes.isEmpty())
        assertTrue(fakeGradleProjectsProvider.cleanupCalled)
    }

    @Test
    fun `GIVEN successful hashing WHEN logging result THEN sends event to event stream`() = runTest {
        fakeGradleProjectsProvider.projectHashingInfos = Result.success(emptyList())

        val result = projectHashService.hashProjects(
            applicationDirectory = Path(tempDir.absolutePath),
            useBuildLogicVersion = false,
            useProjects = emptySet(),
            ciMetadata = testCiMetadata
        )
        projectHashService.logResult(result)

        assertEquals(1, fakeEventStream.receivedResults.size)
        val receivedResult = fakeEventStream.receivedResults.first()
        assertEquals(0, receivedResult.countProjectsHashed)
    }

    @Test
    fun `GIVEN same content WHEN hashing twice THEN produces same hash`() = runTest {
        val testFile = Path(tempDir.absolutePath, "test.txt")
        testFile.writeText("test content")

        val projectInfo = ProjectHashingInfo(
            projectPath = ":test-project",
            projectDirectory = Path(tempDir.absolutePath),
            filesToHash = sequenceOf(testFile)
        )

        fakeGradleProjectsProvider.projectHashingInfos = Result.success(listOf(projectInfo))

        val result1 = projectHashService.hashProjects(
            applicationDirectory = Path(tempDir.absolutePath),
            useBuildLogicVersion = false,
            useProjects = emptySet(),
            ciMetadata = testCiMetadata
        )

        // Create a new service instance to ensure clean state
        val projectHashService2 = ProjectHashService(
            gradleProjectsProvider = fakeGradleProjectsProvider,
            eventStream = fakeEventStream,
            ioDispatcher = Dispatchers.Unconfined,
            defaultDispatcher = Dispatchers.Unconfined
        )

        val result2 = projectHashService2.hashProjects(
            applicationDirectory = Path(tempDir.absolutePath),
            useBuildLogicVersion = false,
            useProjects = emptySet(),
            ciMetadata = testCiMetadata
        )

        assertEquals(result1.projectHashes.first().hash, result2.projectHashes.first().hash)
    }

    @Test
    fun `GIVEN different content WHEN hashing THEN produces different hash`() = runTest {
        val testFile = Path(tempDir.absolutePath, "test.txt")
        testFile.writeText("test content 1")

        val projectInfo = ProjectHashingInfo(
            projectPath = ":test-project",
            projectDirectory = Path(tempDir.absolutePath),
            filesToHash = sequenceOf(testFile)
        )

        fakeGradleProjectsProvider.projectHashingInfos = Result.success(listOf(projectInfo))

        val result1 = projectHashService.hashProjects(
            applicationDirectory = Path(tempDir.absolutePath),
            useBuildLogicVersion = false,
            useProjects = emptySet(),
            ciMetadata = testCiMetadata
        )

        // Change the file content
        testFile.writeText("test content 2")

        val projectHashService2 = ProjectHashService(
            gradleProjectsProvider = fakeGradleProjectsProvider,
            eventStream = fakeEventStream,
            ioDispatcher = Dispatchers.Unconfined,
            defaultDispatcher = Dispatchers.Unconfined
        )

        val result2 = projectHashService2.hashProjects(
            applicationDirectory = Path(tempDir.absolutePath),
            useBuildLogicVersion = false,
            useProjects = emptySet(),
            ciMetadata = testCiMetadata
        )

        assertTrue(result1.projectHashes.first().hash != result2.projectHashes.first().hash)
    }
}
