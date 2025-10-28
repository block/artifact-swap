package xyz.block.artifactswap.core.artifact_checker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xyz.block.artifactswap.core.artifact_checker.models.ArtifactCheckerResult
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArtifactCheckerServiceTest {

    private lateinit var fakeEventStream: FakeArtifactCheckerEventStream
    private lateinit var artifactCheckerService: ArtifactCheckerService

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
        fakeEventStream = FakeArtifactCheckerEventStream()
    }

    private fun createServiceWith(existingArtifacts: Set<Pair<String, String>>): ArtifactCheckerService {
        val fakeArtifactoryService = createFakeArtifactoryService(existingArtifacts)
        return ArtifactCheckerService(
            artifactoryService = fakeArtifactoryService,
            eventStream = fakeEventStream,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun `GIVEN all artifacts exist WHEN checking artifacts THEN returns empty missing list`() = runTest {
        // Setup: All artifacts exist
        artifactCheckerService = createServiceWith(
            setOf(
                "project1" to "1.0.0",
                "project2" to "2.0.0"
            )
        )

        val projectTaskVersions = listOf(
            Triple(":project1", "publish", "1.0.0"),
            Triple(":project2", "publish", "2.0.0")
        )

        val result = artifactCheckerService.checkArtifacts(
            projectTaskVersions = projectTaskVersions,
            ciMetadata = testCiMetadata
        )

        assertEquals(ArtifactCheckerResult.SUCCESS, result.result)
        assertEquals(2, result.countProjectsToCheck)
        assertEquals(2, result.countArtifactsFound)
        assertTrue(result.missingArtifacts.isEmpty())
    }

    @Test
    fun `GIVEN some artifacts missing WHEN checking artifacts THEN returns missing list`() = runTest {
        // Setup: Only one artifact exists
        artifactCheckerService = createServiceWith(
            setOf(
                "project1" to "1.0.0"
            )
        )

        val projectTaskVersions = listOf(
            Triple(":project1", "publish", "1.0.0"),
            Triple(":project2", "publish", "2.0.0")
        )

        val result = artifactCheckerService.checkArtifacts(
            projectTaskVersions = projectTaskVersions,
            ciMetadata = testCiMetadata
        )

        assertEquals(ArtifactCheckerResult.SUCCESS, result.result)
        assertEquals(2, result.countProjectsToCheck)
        assertEquals(1, result.countArtifactsFound)
        assertEquals(1, result.missingArtifacts.size)
        assertTrue(result.missingArtifacts.contains(":project2:publish"))
    }

    @Test
    fun `GIVEN no artifacts exist WHEN checking artifacts THEN returns all as missing`() = runTest {
        // Setup: No artifacts exist
        artifactCheckerService = createServiceWith(emptySet())

        val projectTaskVersions = listOf(
            Triple(":project1", "publish", "1.0.0"),
            Triple(":project2", "publish", "2.0.0"),
            Triple(":project3", "publish", "3.0.0")
        )

        val result = artifactCheckerService.checkArtifacts(
            projectTaskVersions = projectTaskVersions,
            ciMetadata = testCiMetadata
        )

        assertEquals(ArtifactCheckerResult.SUCCESS, result.result)
        assertEquals(3, result.countProjectsToCheck)
        assertEquals(0, result.countArtifactsFound)
        assertEquals(3, result.missingArtifacts.size)
        assertTrue(result.missingArtifacts.containsAll(listOf(":project1:publish", ":project2:publish", ":project3:publish")))
    }

    @Test
    fun `GIVEN empty project list WHEN checking artifacts THEN returns success with zero counts`() = runTest {
        artifactCheckerService = createServiceWith(emptySet())

        val result = artifactCheckerService.checkArtifacts(
            projectTaskVersions = emptyList(),
            ciMetadata = testCiMetadata
        )

        assertEquals(ArtifactCheckerResult.SUCCESS, result.result)
        assertEquals(0, result.countProjectsToCheck)
        assertEquals(0, result.countArtifactsFound)
        assertTrue(result.missingArtifacts.isEmpty())
    }

    @Test
    fun `GIVEN successful check WHEN logging result THEN sends event to event stream`() = runTest {
        artifactCheckerService = createServiceWith(setOf("project1" to "1.0.0"))

        val projectTaskVersions = listOf(
            Triple(":project1", "publish", "1.0.0")
        )

        val result = artifactCheckerService.checkArtifacts(
            projectTaskVersions = projectTaskVersions,
            ciMetadata = testCiMetadata
        )

        artifactCheckerService.logResult(result)

        assertEquals(1, fakeEventStream.receivedResults.size)
        val receivedResult = fakeEventStream.receivedResults.first()
        assertEquals(ArtifactCheckerResult.SUCCESS, receivedResult.result)
        assertEquals(1, receivedResult.countProjectsToCheck)
        assertEquals(1, receivedResult.countArtifactsFound)
    }

    @Test
    fun `GIVEN nested project path WHEN checking artifacts THEN converts path correctly`() = runTest {
        // Setup: Artifact with nested path exists
        artifactCheckerService = createServiceWith(
            setOf(
                "foo_bar_baz" to "1.0.0"
            )
        )

        val projectTaskVersions = listOf(
            Triple(":foo:bar:baz", "publish", "1.0.0")
        )

        val result = artifactCheckerService.checkArtifacts(
            projectTaskVersions = projectTaskVersions,
            ciMetadata = testCiMetadata
        )

        assertEquals(ArtifactCheckerResult.SUCCESS, result.result)
        assertEquals(1, result.countProjectsToCheck)
        assertEquals(1, result.countArtifactsFound)
        assertTrue(result.missingArtifacts.isEmpty())
    }
}
