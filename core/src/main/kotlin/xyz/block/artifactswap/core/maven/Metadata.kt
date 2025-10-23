package xyz.block.artifactswap.core.maven

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

/**
 * POJO representing XML of a "maven-metadata.xml"
 */
@JacksonXmlRootElement(localName = "metadata")
data class Metadata(
  val groupId: String,
  val artifactId: String,
  val versioning: Versioning
)

data class Versioning(
  val latest: String,
  val release: String,
  val versions: Versions,
  val lastUpdated: Long,
)

data class Versions(
  val version: List<String> = emptyList()
)
