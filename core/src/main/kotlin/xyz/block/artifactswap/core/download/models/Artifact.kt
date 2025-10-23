package xyz.block.artifactswap.core.download.models

data class Artifact(
  val groupId: String,
  val artifactId: String,
  val version: String,
  val repo: String = "android-register-sandbags",
)

fun Artifact.toArtifactoryUrl(
    baseArtifactoryUrl: String,
    downloadFileType: DownloadFileType,
): String {
    val groupPath = groupId.replace(".", "/")
    return "$baseArtifactoryUrl/$repo/$groupPath/" +
            "${artifactId}/$version/$artifactId-$version${downloadFileType.pathSuffix}"
}
