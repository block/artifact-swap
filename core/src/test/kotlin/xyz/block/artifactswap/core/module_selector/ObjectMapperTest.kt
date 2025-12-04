package xyz.block.artifactswap.core.module_selector

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xyz.block.artifactswap.core.maven.Dependencies
import xyz.block.artifactswap.core.maven.Dependency
import xyz.block.artifactswap.core.maven.DependencyManagement
import xyz.block.artifactswap.core.maven.Project

class ObjectMapperTest {

  private lateinit var objectMapper: ObjectMapper

  @BeforeEach
  fun setup() {
    objectMapper =
      XmlMapper.builder()
        .defaultUseWrapper(false)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build()
        .registerKotlinModule()
  }

  private val TEST_BOM_XML =
    """
    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
      <groupId>com.squareup.register.sandbags</groupId>
      <artifactId>bom</artifactId>
      <version>0.0.1</version>
      <name>bom</name>
      <dependencyManagement>
        <dependencies>
          <dependency>
            <groupId>com.squareup.register.sandbags</groupId>
            <artifactId>api_applet-ui_public</artifactId>
            <version>A1CCD3E8A6FB63FBE9049494B717A2EF39E8FCC04439F41D0CCEAD857F0B2621</version>
          </dependency>
        </dependencies>
      </dependencyManagement>
      <packaging>pom</packaging>
      <modelVersion>4.0.0</modelVersion>
    </project>

    """
      .trimIndent()

  private val TEST_BOM_OBJECT =
    Project(
      groupId = "com.squareup.register.sandbags",
      artifactId = "bom",
      version = "0.0.1",
      name = "bom",
      dependencyManagement =
        DependencyManagement(
          dependencies =
            Dependencies(
              dependency =
                listOf(
                  Dependency(
                    groupId = "com.squareup.register.sandbags",
                    artifactId = "api_applet-ui_public",
                    version = "A1CCD3E8A6FB63FBE9049494B717A2EF39E8FCC04439F41D0CCEAD857F0B2621",
                  )
                )
            )
        ),
    )

  @Test
  fun `Ensure Project object properly parses to and from string`() {
    val test = objectMapper.readValue<Project>(TEST_BOM_XML)
    assertEquals(
      TEST_BOM_XML,
      objectMapper.writeValueAsString(test),
      "ObjectMapper parsing for Project incorrect! Please check ObjectMapper setup",
    )
    assertEquals(
      TEST_BOM_XML,
      objectMapper.writeValueAsString(TEST_BOM_OBJECT),
      "ObjectMapper parsing for Project incorrect! Please check ObjectMapper setup",
    )
  }
}
