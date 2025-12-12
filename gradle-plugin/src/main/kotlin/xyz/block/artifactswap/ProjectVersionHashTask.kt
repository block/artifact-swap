package xyz.block.artifactswap

import java.security.MessageDigest
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor

/**
 * Task that computes a version hash for a project based on its configured source files.
 *
 * This task replaces the external hashing command by directly using Gradle's knowledge of the
 * project's sourcesets to determine which files should be included in the hash computation.
 */
@CacheableTask
abstract class ProjectVersionHashTask : DefaultTask() {

  /** The source files to include in the hash computation. */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val sourceFiles: ConfigurableFileCollection

  /**
   * Optional hash seed to include in the hash computation. This can be used to force new versions
   * when build configuration or other non-source factors change.
   */
  @get:Input abstract val hashSeed: Property<String>

  /** The computed version hash. */
  @get:OutputFile abstract val outputFile: RegularFileProperty

  @get:Inject abstract val workerExecutor: WorkerExecutor

  init {
    group = "artifactswap"
    description = "Computes a version hash for the project based on its source files"
    hashSeed.convention("")
  }

  @TaskAction
  fun computeHash() {
    val workQueue = workerExecutor.noIsolation()
    workQueue.submit(HashComputationAction::class.java) { params ->
      params.sourceFiles.from(sourceFiles)
      params.hashSeed.set(hashSeed)
      params.outputFile.set(outputFile)
    }
  }
}

/** Work action that performs the actual hash computation. */
abstract class HashComputationAction : WorkAction<HashComputationAction.Parameters> {

  @get:Inject abstract val fileSystemOperations: FileSystemOperations

  interface Parameters : WorkParameters {
    val sourceFiles: ConfigurableFileCollection
    val hashSeed: Property<String>
    val outputFile: RegularFileProperty
  }

  override fun execute() {
    val messageDigest = MessageDigest.getInstance("SHA-256")

    // Add hash seed if present
    val seed = parameters.hashSeed.orNull
    if (!seed.isNullOrEmpty()) {
      messageDigest.update(seed.toByteArray())
    }

    // Hash all source files in sorted order for consistency
    parameters.sourceFiles.files
      .filter { it.isFile }
      .sortedBy { it.absolutePath }
      .forEach { file -> messageDigest.update(file.readBytes()) }

    // Convert hash to hex string
    val hash = messageDigest.digest().toHexString()

    // Write the hash to the output file
    val outputFile = parameters.outputFile.asFile.get()
    outputFile.parentFile?.mkdirs()
    outputFile.writeText(hash)
  }

  private fun ByteArray.toHexString(): String {
    return joinToString("") { "%02X".format(it) }
  }
}
