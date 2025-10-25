package xyz.block.artifactswap.core.download.models

enum class ArtifactDownloaderResult {
  SUCCESS,
  // we searched recent commits but didn't find any that had a published BOM
  FAILED_TO_FIND_VALID_BOM_VERSION,
  FAILED_TO_DOWNLOAD_BOM,
  // when more than 10% of downloads failed
  MANY_DOWNLOADS_FAILED,
  // when more than 10% of installs failed
  MANY_INSTALLS_FAILED,
  // indicates we forgot to set this value
  NOT_SET
}
