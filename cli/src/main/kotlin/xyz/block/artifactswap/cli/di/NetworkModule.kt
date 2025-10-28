package xyz.block.artifactswap.cli.di

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.eventstream.EventstreamService
import okhttp3.Dispatcher
import xyz.block.artifactswap.core.network.ArtifactoryEndpoints
import xyz.block.artifactswap.core.network.ArtifactoryService
import okhttp3.OkHttpClient
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.create
import xyz.block.artifactswap.cli.network.EventStreamLoggingEnvironment
import xyz.block.artifactswap.core.config.ArtifactSwapConfigHolder
import kotlin.io.path.Path
import kotlin.io.path.readLines
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val UNAUTHENTICATED_HTTP_METHODS = listOf("GET", "HEAD")

internal fun artifactoryNetworkModule() = module {
    single<OkHttpClient>(named("artifactoryClient")) {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(30.seconds.toJavaDuration())
            .readTimeout(30.seconds.toJavaDuration())
            .callTimeout(30.seconds.toJavaDuration())
            .dispatcher(Dispatcher().apply {
                maxRequestsPerHost = 128
                maxRequests = 512
            })
            .addInterceptor { chain ->
                // GET/HEAD methods don't require authentication
                if (chain.request().method !in UNAUTHENTICATED_HTTP_METHODS) {
                    val newRequest = chain.request()
                        .newBuilder()
                        .addHeader(
                            "Authorization",
                            "Bearer ${get<String>(named("artifactoryToken"))}"
                        )
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
        Path(get<String>(named("artifactorySecretsPath")))
            .resolve(ArtifactSwapConfigHolder.instance.artifactoryPublisherTokenFileName)
            .readLines()
            .first()
    }

    single<Retrofit>(named("artifactoryRetrofit")) {
        Retrofit.Builder()
            .baseUrl(get<String>(named("artifactoryBaseUrl")))
            .client(get<OkHttpClient>(named("artifactoryClient")))
            .addConverterFactory(JacksonConverterFactory.create(get()))
            .build()
    }

    single<ArtifactoryService> {
        ArtifactoryService(
            get<ArtifactoryEndpoints>(),
        )
    }

    single<ArtifactoryEndpoints> { get<Retrofit>(named("artifactoryRetrofit")).create<ArtifactoryEndpoints>() }

    single(named("artifactoryBaseUrl")) { ArtifactSwapConfigHolder.instance.artifactoryBaseUrl }

    single(named("artifactorySecretsPath")) { System.getenv("SECRETS_PATH") }
}


const val EVENT_STREAM_NAME = "analyticsModuleEventStream"

internal fun analyticsNetworkModule() = module {
    single<EventstreamService> {
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(ArtifactSwapConfigHolder.instance.eventstreamBaseUrl)
            .client(httpClient)
            .addConverterFactory(retrofit2.converter.wire.WireConverterFactory.create())
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
