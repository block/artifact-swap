package xyz.block.artifactswap.core.download.models

enum class DownloadFileType(val pathSuffix: String) {
  POM(".pom"),
  AAR(".aar"),
  JAR(".jar"),
  MODULE(".module"),
  SOURCES_JAR("-sources.jar")
}
