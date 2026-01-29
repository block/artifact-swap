package xyz.block.artifactswap.idea.gradle

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import kotlinx.coroutines.flow.StateFlow

/**
 * Suppresses Gradle-related inspections for type-safe project accessors that are known to be valid
 * based on the all-projects.txt file.
 *
 * This handles the case where Spotlight loads only a subset of projects but the build file
 * references other valid projects via type-safe accessors (e.g., `projects.account.backend.api`).
 *
 * Suppressed inspections:
 * - DependencyNotationArgument: "Unrecognized dependency notation"
 * - GrUnresolvedAccess: "No candidates found for method call" (Groovy)
 */
class GradleBuildFileInspectionSuppressor : InspectionSuppressor {

  companion object {
    private val SUPPRESSED_INSPECTIONS =
      setOf(
        "DependencyNotationArgument", // "Unrecognized dependency notation"
        "GrUnresolvedAccess", // "No candidates found for method call" (Groovy)
      )
  }

  override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
    if (toolId !in SUPPRESSED_INSPECTIONS) return false

    val file = element.containingFile?.virtualFile ?: return false
    if (!GradleProjectPathUtils.isGradleBuildFile(file.name)) return false

    // Walk up to find the full expression containing projects.*
    val text = findProjectsAccessorText(element) ?: return false

    // Validate against all-projects.txt
    val project = element.project
    if (project.isDisposed) return false

    val allProjects =
      try {
        getAllProjectsFromSpotlight(project)
      } catch (_: Exception) {
        return false
      }
    if (allProjects.isEmpty()) return false

    val accessorMap = GradleProjectPathUtils.buildAccessorMap(allProjects)
    val cleanAccessor = GradleProjectPathUtils.cleanTypeSafeAccessor(text)

    return GradleProjectPathUtils.isValidAccessor(cleanAccessor, accessorMap)
  }

  /**
   * Gets the set of all projects from Spotlight via reflection. This is necessary because Spotlight
   * is a plugin dependency (runtime only).
   */
  @Suppress("UNCHECKED_CAST")
  private fun getAllProjectsFromSpotlight(project: Project): Set<GradlePath> {
    // Load the SpotlightProjectService class
    val serviceClass = Class.forName("com.fueledbycaffeine.spotlight.idea.SpotlightProjectService")

    // Get the service instance from the project
    val spotlightService = project.getService(serviceClass)

    // Get the allProjects StateFlow
    val allProjectsMethod = serviceClass.getMethod("getAllProjects")
    val allProjectsFlow = allProjectsMethod.invoke(spotlightService) as StateFlow<*>

    // Get the current value
    val spotlightProjects = allProjectsFlow.value as? Set<*> ?: return emptySet()

    // Convert Spotlight's GradlePath to our local GradlePath
    return spotlightProjects
      .mapNotNull { gradlePath ->
        val pathMethod = gradlePath!!::class.java.getMethod("getPath")
        val path = pathMethod.invoke(gradlePath) as? String
        path?.let { GradlePath(it) }
      }
      .toSet()
  }

  /** Walks up the PSI tree to find the full projects.* accessor expression. */
  private fun findProjectsAccessorText(element: PsiElement): String? {
    var current: PsiElement? = element

    // Walk up to find an element whose text starts with "projects."
    while (current != null) {
      val text = current.text
      if (text.startsWith("projects.")) {
        // Extract just the accessor part using regex
        val match = GradleProjectPathUtils.TYPE_SAFE_ACCESSOR_PATTERN.find(text)
        return match?.value
      }
      current = current.parent
    }

    return null
  }

  override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> {
    return SuppressQuickFix.EMPTY_ARRAY
  }
}
