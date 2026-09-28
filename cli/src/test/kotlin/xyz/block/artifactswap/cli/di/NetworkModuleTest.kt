package xyz.block.artifactswap.cli.di

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class NetworkModuleTest {
  private lateinit var server: HttpServer
  private val receivedRequests = CopyOnWriteArrayList<Pair<String, String?>>()

  @BeforeEach
  fun setUp() {
    server = HttpServer.create(InetSocketAddress(0), 0)
    server.createContext("/") { exchange ->
      receivedRequests +=
        exchange.requestMethod to exchange.requestHeaders.getFirst("Authorization")
      exchange.sendResponseHeaders(200, -1)
      exchange.close()
    }
    server.start()
  }

  @AfterEach
  fun tearDown() {
    server.stop(0)
  }

  @Test
  fun `artifactory client omits authorization when token is not configured`() {
    executeRequests(token = "", methods = listOf("GET"))

    val expectedRequests: List<Pair<String, String?>> = listOf("GET" to null)
    assertEquals(expectedRequests, receivedRequests)
  }

  @Test
  fun `artifactory client authenticates every HTTP method when token is configured`() {
    executeRequests(token = "test-token", methods = listOf("GET", "HEAD", "POST"))

    val expectedRequests: List<Pair<String, String?>> =
      listOf(
        "GET" to "Bearer test-token",
        "HEAD" to "Bearer test-token",
        "POST" to "Bearer test-token",
      )
    assertEquals(expectedRequests, receivedRequests)
  }

  private fun executeRequests(token: String, methods: List<String>) {
    val application = koinApplication { allowOverride(true) }
    application.modules(
      artifactoryNetworkModule(),
      module { single(named("artifactoryToken")) { token } },
    )
    val client = application.koin.get<OkHttpClient>(named("artifactoryClient"))

    try {
      methods.forEach { method ->
        val body = if (method == "POST") "content".toRequestBody() else null
        val request =
          Request.Builder()
            .url("http://localhost:${server.address.port}/artifact")
            .method(method, body)
            .build()

        client.newCall(request).execute().close()
      }
    } finally {
      client.cache?.close()
      application.close()
    }
  }
}
