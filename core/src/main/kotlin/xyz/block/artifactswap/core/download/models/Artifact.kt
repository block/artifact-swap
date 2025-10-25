package xyz.block.artifactswap.core.download.models

import xyz.block.artifactswap.core.config.ArtifactSwapConfigHolder

data class Artifact(
  val groupId: String,
  val artifactId: String,
  val version: String,
  val repo: String = ArtifactSwapConfigHolder.instance.primaryRepositoryName,
)

fun Artifact.toArtifactoryUrl(
    baseArtifactoryUrl: String,
    downloadFileType: DownloadFileType,
): String {
    val groupPath = groupId.replace(".", "/")
    return "$baseArtifactoryUrl/$repo/$groupPath/" +
            "${artifactId}/$version/$artifactId-$version${downloadFileType.pathSuffix}"
}
