package xyz.block.artifactswap.cli.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import org.koin.dsl.module

internal fun coroutinesModule() = module {
    single(named("IO")) { Dispatchers.IO }
    single(named("Default")) { Dispatchers.Default }
}

val KoinApplication.ioDispatcher: CoroutineDispatcher
    get() = koin.get(named("IO"))

val KoinApplication.defaultDispatcher: CoroutineDispatcher
    get() = koin.get(named("Default"))
