package xyz.block.artifactswap.core.maven

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

/**
 * POJO representing XML of a maven POM
 */
@JacksonXmlRootElement(localName = "project")
data class Project(
  val groupId: String,
  val artifactId: String,
  val version: String,
  val name: String,
  val dependencyManagement: DependencyManagement,
  @JacksonXmlProperty(isAttribute = true)
  val xmlns: String = "http://maven.apache.org/POM/4.0.0",
  @JacksonXmlProperty(isAttribute = true, localName = "xmlns:xsi")
  val xsi: String = "http://www.w3.org/2001/XMLSchema-instance",
  @JacksonXmlProperty(isAttribute = true, localName = "xsi:schemaLocation")
  val schemaLocation: String = "http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd",
  val packaging: String = "pom",
  val modelVersion: String = "4.0.0"
)

data class DependencyManagement(
  val dependencies: Dependencies
)

data class Dependencies(
  val dependency: List<Dependency>
)

data class Dependency(
  val groupId: String,
  val artifactId: String,
  val version: String,
)
