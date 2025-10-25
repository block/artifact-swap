package xyz.block.artifactswap.core.utils

import kotlin.time.Duration

val Duration.inWholeMillisecondsIfFinite: Long
  get() =  if (isFinite()) inWholeMilliseconds else -1L
