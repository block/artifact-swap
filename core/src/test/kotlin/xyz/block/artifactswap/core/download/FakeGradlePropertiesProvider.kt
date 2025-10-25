package xyz.block.artifactswap.core.download

import xyz.block.artifactswap.core.gradle.GradlePropertiesProvider

class FakeGradlePropertiesProvider : GradlePropertiesProvider {
    override fun get(key: String): String {
        return "1.2.3"
    }
}
