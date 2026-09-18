package xyz.block.artifactswap

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
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
  /**
   * @param ioContext the coroutine context used for I/O work. Pass [Dispatchers.IO] to run the
   *   selection steps in parallel, or [EmptyCoroutineContext] to keep every step on the calling
   *   thread. The latter is required inside a [org.gradle.api.provider.ValueSource] when the
   *   configuration cache is active, because Gradle only exempts the thread that called `obtain()`
   *   from input tracking (https://github.com/gradle/gradle/issues/36121).
   */
  fun create(
    rootDir: Path,
    config: ArtifactSwapConfig,
    spotlightRules: Set<DependencyRule>,
    ioContext: CoroutineContext = Dispatchers.IO,
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
    val squareGit = RealSquareGit(rootDir, ioContext)
    val localArtifactRepository = RealLocalArtifactRepository(xmlMapper, ioContext, config = config)

    // Create download package instances for BOM loading
    val downloadSquareGit = RealSquareGit(rootDir, ioContext)
    val localMavenPath =
      Path.of(config.mavenLocalDirectory.replace("\${user.home}", System.getProperty("user.home")))
    val downloadArtifactRepository =
      RealArtifactRepository(localMavenPath, artifactoryEndpoints, ioContext, xmlMapper, config)
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
      ioContext,
      eventstream,
      spotlightRules,
      alwaysKeepProjects,
    )
  }
}
