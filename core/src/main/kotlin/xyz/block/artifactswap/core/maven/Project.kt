package xyz.block.artifactswap.core.maven

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

/** POJO representing XML of a maven POM */
@JacksonXmlRootElement(localName = "project")
data class Project(
  val groupId: String,
  val artifactId: String,
  val version: String,
  val name: String,
  val dependencyManagement: DependencyManagement? = null,
  @JacksonXmlProperty(isAttribute = true) val xmlns: String = "http://maven.apache.org/POM/4.0.0",
  @JacksonXmlProperty(isAttribute = true, localName = "xmlns:xsi")
  val xsi: String = "http://www.w3.org/2001/XMLSchema-instance",
  @JacksonXmlProperty(isAttribute = true, localName = "xsi:schemaLocation")
  val schemaLocation: String =
    "http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd",
  val packaging: String = "pom",
  val modelVersion: String = "4.0.0",
) {
  fun artifactDependencies(artifactDepGroup: String): List<Dependency> {
    val depManagementDependencies = dependencyManagement?.dependencies?.dependency ?: emptyList()
    return depManagementDependencies.filter { it.groupId == artifactDepGroup }
  }
}

data class DependencyManagement(val dependencies: Dependencies)

data class Dependencies(val dependency: List<Dependency>)

// Note, technically versions can be `null` in general POMs, but we are using this to check
// actual versions of artifacts in a BOM, so they should be present
data class Dependency(val groupId: String, val artifactId: String, val version: String)
