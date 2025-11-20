package xyz.block.artifactswap.core.download.models

enum class ArtifactDownloaderResult(val exitCode: Int) {
  SUCCESS(0),
  // we searched recent commits but didn't find any that had a published BOM
  FAILED_TO_FIND_VALID_BOM_VERSION(101),
  FAILED_TO_DOWNLOAD_BOM(102),
  // when more than 10% of downloads failed
  MANY_DOWNLOADS_FAILED(103),
  // when more than 10% of installs failed
  MANY_INSTALLS_FAILED(104),
  // indicates we forgot to set this value
  NOT_SET(1),
}
