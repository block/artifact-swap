package xyz.block.artifactswap.core.download.services

fun Int.is4xx(): Boolean {
  return this in 400..499
}
