package xyz.block.artifactswap

import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class ProjectAccessorProxyTest {

  static final String ARTIFACTS_GROUP = "com.example.artifacts"

  @Test
  void proxyWithNullWrappedConvertsToArtifactNotation() {
    def proxy = new ProjectAccessorWrapper(null, ARTIFACTS_GROUP, ["di", "scoping"])

    assertEquals("com.example.artifacts:di_scoping", proxy.toString())
    assertEquals("com.example.artifacts:di_scoping", proxy.notation)
  }

  @Test
  void proxyConvertsCamelCaseToKebabCase() {
    def proxy = new ProjectAccessorWrapper(null, ARTIFACTS_GROUP, ["featureFlags", "api"])

    assertEquals("com.example.artifacts:feature-flags_api", proxy.toString())
  }

  @Test
  void proxyImplementsCharSequence() {
    def proxy = new ProjectAccessorWrapper(null, ARTIFACTS_GROUP, ["common", "utils"])
    def expected = "com.example.artifacts:common_utils"

    assertEquals(expected.length(), proxy.length())
    assertEquals('c' as char, proxy.charAt(0))
    assertEquals("com.example", proxy.subSequence(0, 11).toString())
  }

  @Test
  void propertyAccessOnNullWrappedProxyReturnsNewProxy() {
    def proxy = new ProjectAccessorWrapper(null, ARTIFACTS_GROUP, ["parent"])

    def child = proxy.child

    assertTrue(child instanceof ProjectAccessorWrapper)
    assertEquals("com.example.artifacts:parent_child", child.toString())
  }

  @Test
  void chainedPropertyAccessBuildsCorrectPath() {
    def proxy = new ProjectAccessorWrapper(null, ARTIFACTS_GROUP, [])

    def result = proxy.checkoutFeature.widgets.views

    assertEquals("com.example.artifacts:checkout-feature_widgets_views", result.toString())
  }

  @Test
  void proxyWithWrappedAccessorHandlesNonGradleAccessorProperties() {
    // When wrapped object has a property that's not a Gradle accessor,
    // the proxy returns the raw value (if accessible) or creates a new proxy
    def mockAccessor = new Expando()
    mockAccessor.metaClass.someProperty = "raw-value"
    def proxy = new ProjectAccessorWrapper(mockAccessor, ARTIFACTS_GROUP, ["parent"])

    // Properties added via metaClass ARE accessible
    assertEquals("raw-value", proxy.someProperty)
  }

  @Test
  void proxyWithWrappedAccessorReturnsProxyForMissingProperties() {
    def mockAccessor = new Expando()
    def proxy = new ProjectAccessorWrapper(mockAccessor, ARTIFACTS_GROUP, ["parent"])

    def result = proxy.missingChild

    assertTrue(result instanceof ProjectAccessorWrapper)
    assertEquals("com.example.artifacts:parent_missing-child", result.toString())
  }

  @Test
  void singlePathSegmentWorksCorrectly() {
    def proxy = new ProjectAccessorWrapper(null, ARTIFACTS_GROUP, ["singleModule"])

    assertEquals("com.example.artifacts:single-module", proxy.toString())
  }

  @Test
  void deeplyNestedPathWorksCorrectly() {
    def proxy = new ProjectAccessorWrapper(null, ARTIFACTS_GROUP, [])

    def result = proxy.level1.level2.level3.level4.leaf

    assertEquals("com.example.artifacts:level1_level2_level3_level4_leaf", result.toString())
  }

  @Test
  void multipleUppercaseLettersInSegmentAreHandledCorrectly() {
    def proxy = new ProjectAccessorWrapper(null, ARTIFACTS_GROUP, ["myUiComponent", "api"])

    assertEquals("com.example.artifacts:my-ui-component_api", proxy.toString())
  }
}
