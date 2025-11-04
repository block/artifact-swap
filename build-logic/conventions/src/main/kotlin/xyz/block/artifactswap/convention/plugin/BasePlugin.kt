@file:Suppress("unused")

package xyz.block.artifactswap.convention.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class BasePlugin : Plugin<Project> {
  override fun apply(target: Project): Unit = target.run{
    plugins.apply("org.jetbrains.kotlin.jvm")
    tasks.withType(KotlinCompile::class.java).configureEach { task ->
      task.compilerOptions {
        jvmTarget.set(JVM_TARGET)
        freeCompilerArgs.addAll(
          "-Xannotation-default-target=param-property",
        )
      }
    }
    tasks.withType(GroovyCompile::class.java).configureEach { task ->
      task.options.release.set(JVM_TARGET.target.toInt())
    }
    tasks.withType(JavaCompile::class.java).configureEach { task ->
      task.options.release.set(JVM_TARGET.target.toInt())
    }
  }

  private companion object {
    val JVM_TARGET = JvmTarget.JVM_17
  }
}