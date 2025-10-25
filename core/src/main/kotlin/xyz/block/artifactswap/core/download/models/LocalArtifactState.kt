package xyz.block.artifactswap.core.download.models

/**
 * Represents the state of a local artifact. If it's in our .m2, it's installed,
 * otherwise not.
 */
enum class LocalArtifactState {
  INSTALLED,
  NOT_INSTALLED
}
