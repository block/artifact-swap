package xyz.block.artifactswap.model

/** Default implementation of [ArtifactSwapModel]. */
data class DefaultArtifactSwapModel(
  override val mavenGroup: String,
  override val bomVersion: String,
  override val mavenLocalDirectory: String,
) : ArtifactSwapModel
