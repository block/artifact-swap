package xyz.block.artifactswap.cli

import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.kotlin.logger
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import picocli.CommandLine
import xyz.block.artifactswap.cli.di.analyticsNetworkModule
import xyz.block.artifactswap.cli.di.artifactoryNetworkModule
import xyz.block.artifactswap.cli.di.commonModule
import xyz.block.artifactswap.cli.di.coroutinesModule
import xyz.block.artifactswap.cli.di.gradleModule
import xyz.block.artifactswap.cli.options.CommonOptions
import xyz.block.artifactswap.cli.options.GradleOptions
import java.util.concurrent.Callable

/**
 * Base command to be used by all Artifact Swap tool commands
 *
 * This injects common Koin modules used by all (or most) commands
 */
abstract class AbstractArtifactSwapCommand : Callable<Int> {

    @CommandLine.Spec
    private lateinit var spec: CommandLine.Model.CommandSpec

    @CommandLine.Mixin
    private lateinit var gradleOptions: GradleOptions

    @CommandLine.Mixin
    private lateinit var commonOptions: CommonOptions

    private val application by lazy {
        return@lazy koinApplication {
            modules(
                coroutinesModule(),
                commonModule(commonOptions),
                gradleModule(gradleOptions),
                artifactoryNetworkModule(),
                analyticsNetworkModule()
            )
        }
    }

    /**
     * This should not be overridden by subclasses.
     */
    final override fun call(): Int {
        try {
            runBlocking {
                init(application)
                executeCommand(application)
            }
            return spec.exitCodeOnSuccess()
        } catch (e: Throwable) {
            logger.error(e)
            return spec.exitCodeOnExecutionException()
        }
    }

    /**
     * Entry for command.
     *
     * @param application [KoinApplication] loaded for this command
     */
    abstract fun init(application: KoinApplication)

    /**
     * Entry for command.
     *
     * @param application [KoinApplication] loaded for this command
     */
    abstract suspend fun executeCommand(application: KoinApplication)
}
