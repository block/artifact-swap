package xyz.block.artifactswap.cli.di

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.readLines
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import okhttp3.Cache
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.converter.wire.WireConverterFactory
import retrofit2.create
import xyz.block.artifactswap.core.config.ArtifactSwapConfig
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.eventstream.EventstreamService
import xyz.block.artifactswap.core.network.ArtifactoryEndpoints
import xyz.block.artifactswap.core.network.ArtifactoryService

private val UNAUTHENTICATED_HTTP_METHODS = listOf("GET", "HEAD")

internal fun artifactoryNetworkModule() = module {
  // HTTP cache for OkHttp client (10MB)
  // It's small because we only cache error responses from the repo
  single<Cache> {
    val cacheDirectory = File(System.getProperty("java.io.tmpdir"), "artifactswap-cache")
    Cache(cacheDirectory, 10L * 1024L * 1024L)
  }

  single<HttpLoggingInterceptor> {
    val logger = LoggerFactory.getLogger("http")
    HttpLoggingInterceptor { message -> logger.trace(message) }
      .apply { level = HttpLoggingInterceptor.Level.BASIC }
  }

  // Interceptor that caches only 404 responses for 1 hour and prevents caching of successful
  // responses.
  // Successful artifact downloads are implicitly cached in the local .m2 repository.
  // Missing artifacts are expensive to query from the repo backend and should be cached.
  single<Interceptor>(named("cache404Interceptor")) {
    Interceptor { chain ->
      val response = chain.proceed(chain.request())

      when (response.code) {
        // Cache 404 responses for 1 hour
        404 -> {
          response
            .newBuilder()
            .header("Cache-Control", "public, max-age=3600, immutable")
            .removeHeader("Pragma")
            .removeHeader("Expires")
            .build()
        }
        // Prevent caching of successful responses (artifacts are cached in local .m2 repo)
        in 200..299 -> {
          response
            .newBuilder()
            .header("Cache-Control", "no-store")
            .removeHeader("Pragma")
            .removeHeader("Expires")
            .build()
        }
        // Don't modify other response codes
        else -> response
      }
    }
  }

  single<OkHttpClient>(named("artifactoryClient")) {
    OkHttpClient.Builder()
      .cache(get<Cache>())
      .retryOnConnectionFailure(true)
      .connectTimeout(15.seconds.toJavaDuration())
      .readTimeout(30.seconds.toJavaDuration())
      .callTimeout(30.seconds.toJavaDuration())
      .dispatcher(
        Dispatcher().apply {
          maxRequestsPerHost = 128
          maxRequests = 512
        }
      )
      .addInterceptor(get<HttpLoggingInterceptor>())
      .addNetworkInterceptor(get<Interceptor>(named("cache404Interceptor")))
      .addInterceptor { chain ->
        // GET/HEAD methods don't require authentication
        if (chain.request().method !in UNAUTHENTICATED_HTTP_METHODS) {
          val newRequest =
            chain
              .request()
              .newBuilder()
              .addHeader("Authorization", "Bearer ${get<String>(named("artifactoryToken"))}")
              .build()
          return@addInterceptor chain.proceed(newRequest)
        }
        return@addInterceptor chain.proceed(chain.request())
      }
      .build()
  }

  single<ObjectMapper> {
    XmlMapper.builder()
      .defaultUseWrapper(false)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .enable(SerializationFeature.INDENT_OUTPUT)
      .build()
      .registerKotlinModule()
  }

  single(named("artifactoryToken")) {
    val config = get<ArtifactSwapConfig>()
    Path(get<String>(named("artifactorySecretsPath")))
      .resolve(config.artifactoryPublisherTokenFileName)
      .readLines()
      .first()
  }

  single<Retrofit>(named("artifactoryRetrofit")) {
    Retrofit.Builder()
      .baseUrl(get<ArtifactSwapConfig>().artifactoryBaseUrl)
      .client(get<OkHttpClient>(named("artifactoryClient")))
      .addConverterFactory(JacksonConverterFactory.create(get()))
      .build()
  }

  single<ArtifactoryService> { ArtifactoryService(get<ArtifactoryEndpoints>(), get()) }

  single<ArtifactoryEndpoints> {
    get<Retrofit>(named("artifactoryRetrofit")).create<ArtifactoryEndpoints>()
  }

  single(named("artifactorySecretsPath")) { System.getenv("SECRETS_PATH") }
}

const val EVENT_STREAM_NAME = "analyticsModuleEventStream"

internal fun analyticsNetworkModule() = module {
  single<EventstreamService> {
    val config = get<ArtifactSwapConfig>()
    val httpClient =
      OkHttpClient.Builder()
        .connectTimeout(5.seconds.toJavaDuration())
        .readTimeout(10.seconds.toJavaDuration())
        .writeTimeout(10.seconds.toJavaDuration())
        .build()

    Retrofit.Builder()
      .baseUrl(config.eventstreamBaseUrl)
      .client(httpClient)
      .addConverterFactory(WireConverterFactory.create())
      .build()
      .create<EventstreamService>()
  }

  single<Eventstream>(named(EVENT_STREAM_NAME)) {
    Eventstream(eventstreamService = get<EventstreamService>())
  }
}

val KoinApplication.artifactoryService: ArtifactoryService
  get() = koin.get()

val KoinApplication.artifactoryEndpoints: ArtifactoryEndpoints
  get() = koin.get()

val KoinApplication.eventStream: Eventstream
  get() = koin.get(named(EVENT_STREAM_NAME))
