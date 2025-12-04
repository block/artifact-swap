package xyz.block.artifactswap

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.jvm.java
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import xyz.block.artifactswap.core.config.ArtifactSwapConfig
import xyz.block.artifactswap.core.download.services.ArtifactSyncBomLoader
import xyz.block.artifactswap.core.download.services.RealArtifactRepository
import xyz.block.artifactswap.core.download.services.RealArtifactSyncBomLoader
import xyz.block.artifactswap.core.eventstream.Eventstream
import xyz.block.artifactswap.core.eventstream.EventstreamService
import xyz.block.artifactswap.core.eventstream.defaultMoshi
import xyz.block.artifactswap.core.module_selector.AlwaysKeepProjectsList
import xyz.block.artifactswap.core.module_selector.ArtifactSwapModuleSelector
import xyz.block.artifactswap.core.module_selector.RealArtifactSwapModuleSelector
import xyz.block.artifactswap.core.network.ArtifactoryEndpoints
import xyz.block.artifactswap.core.network.ArtifactoryService
import xyz.block.artifactswap.core.repository.RealLocalArtifactRepository
import xyz.block.artifactswap.core.shared_services.git.RealSquareGit

internal object ArtifactSwapModuleSelectorFactory {
  fun create(
    rootDir: Path,
    config: ArtifactSwapConfig,
    spotlightRules: Set<DependencyRule>,
  ): ArtifactSwapModuleSelector {
    val xmlMapper =
      XmlMapper.builder()
        .defaultUseWrapper(false)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()
        .registerKotlinModule()

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
    val localArtifactRepository =
      RealLocalArtifactRepository(xmlMapper, Dispatchers.IO, config = config)

    // Create download package instances for BOM loading
    val downloadSquareGit = RealSquareGit(rootDir, Dispatchers.IO)
    val localMavenPath = Paths.get(System.getProperty("user.home")).resolve(".m2/repository")
    val downloadArtifactRepository =
      RealArtifactRepository(
        localMavenPath,
        artifactoryEndpoints,
        Dispatchers.IO,
        xmlMapper,
        config,
      )
    val bomLoader: ArtifactSyncBomLoader =
      RealArtifactSyncBomLoader(
        downloadSquareGit,
        downloadArtifactRepository,
        artifactoryService,
        config,
      )

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
      bomLoader,
      Dispatchers.IO,
      eventstream,
      spotlightRules,
      alwaysKeepProjects,
    )
  }
}
