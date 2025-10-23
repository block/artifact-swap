package xyz.block.artifactswap.cli.di

import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import org.koin.dsl.module
import xyz.block.artifactswap.cli.options.CommonOptions
import java.nio.file.Path

internal fun commonModule(commonOptions: CommonOptions) = module {

    single(named("directory")) { commonOptions.directory }

    single(named("loggingEnvironment")) { commonOptions.loggingEnvironment }

    single(named("dryRun")) { commonOptions.dryRun }
}

val KoinApplication.directory: Path
    get() = koin.get(named("directory"))

val KoinApplication.dryRun: Boolean
    get() = koin.get(named("dryRun"))
