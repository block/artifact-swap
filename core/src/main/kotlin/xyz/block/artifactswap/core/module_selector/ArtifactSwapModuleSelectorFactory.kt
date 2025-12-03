package xyz.block.artifactswap.core.module_selector

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import xyz.block.artifactswap.core.config.ArtifactSwapConfig
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.eventstream.EventstreamService
import xyz.block.artifactswap.core.eventstream.defaultMoshi
import xyz.block.artifactswap.core.network.ArtifactoryEndpoints
import xyz.block.artifactswap.core.network.ArtifactoryService

object ArtifactSwapModuleSelectorFactory {
  fun create(rootDir: Path, config: ArtifactSwapConfig): ArtifactSwapModuleSelector {
    val xmlMapper =
      XmlMapper().apply {
        registerModule(KotlinModule.Builder().build())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      }

    val okHttpClient =
      OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .connectTimeout(15.seconds.toJavaDuration())
        .readTimeout(30.seconds.toJavaDuration())
        .callTimeout(30.seconds.toJavaDuration())
        .build()

    val retrofit =
      Retrofit.Builder()
        .baseUrl(config.artifactoryBaseUrl)
        .client(okHttpClient)
        .addConverterFactory(JacksonConverterFactory.create(xmlMapper))
        .build()

    val artifactoryEndpoints = retrofit.create(ArtifactoryEndpoints::class.java)
    val artifactoryService = ArtifactoryService(artifactoryEndpoints, config)
    val squareGit = RealSquareGit(rootDir, Dispatchers.IO)
    val localArtifactRepository = RealLocalArtifactRepository(xmlMapper, Dispatchers.IO)
    val bomHelper =
      RealArtifactSwapBomHelper(squareGit, localArtifactRepository, artifactoryService)

    val eventstreamService =
      retrofit
        .newBuilder()
        .client(okHttpClient)
        .baseUrl(config.eventstreamBaseUrl)
        .build()
        .create(EventstreamService::class.java)
    val eventstream = Eventstream(eventstreamService = eventstreamService, moshi = defaultMoshi)

    val alwaysKeepProjects = AlwaysKeepProjectsList.read(rootDir)

    return RealArtifactSwapModuleSelector(
      localArtifactRepository,
      squareGit,
      bomHelper,
      Dispatchers.IO,
      eventstream,
      alwaysKeepProjects,
    )
  }
}
