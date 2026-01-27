package xyz.block.artifactswap.idea.util

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.MethodSignatureUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty

/**
 * Resolves all references from this element. Handles multi-resolve references (like Android
 * resources with multiple variants) and single references.
 */
fun PsiElement.resolveAllReferences(): List<PsiElement> {
  val results = mutableListOf<PsiElement>()

  // Try direct reference on the element
  val directRef = reference
  if (directRef != null) {
    // Check if it's a multi-resolve reference (e.g., Android resources with variants)
    if (directRef is PsiPolyVariantReference) {
      val resolveResults = directRef.multiResolve(false)
      resolveResults.mapNotNullTo(results) { it.element }
    } else {
      // Single resolve
      val resolved = directRef.resolve()
      if (resolved != null) {
        results.add(resolved)
      }
    }
  }

  // If no results yet, try reference on parent
  if (results.isEmpty()) {
    val parentRef = parent?.reference
    if (parentRef != null) {
      if (parentRef is PsiPolyVariantReference) {
        val resolveResults = parentRef.multiResolve(false)
        resolveResults.mapNotNullTo(results) { it.element }
      } else {
        val resolved = parentRef.resolve()
        if (resolved != null) {
          results.add(resolved)
        }
      }
    }
  }

  return results
}

/** Gets the name of this element if it's a named element. */
fun PsiElement.getElementName(): String? {
  return when (this) {
    is PsiNamedElement -> name
    else -> null
  }
}

/** Finds a class by name in this file (supports both Java and Kotlin classes). */
fun PsiFile.findClassByName(name: String): PsiElement? {
  // Try Java classes first
  PsiTreeUtil.findChildrenOfType(this, PsiClass::class.java)
    .find { it.name == name }
    ?.let {
      return it
    }

  // Try Kotlin classes
  PsiTreeUtil.findChildrenOfType(this, KtClass::class.java)
    .find { it.name == name }
    ?.let {
      return it
    }

  return null
}

/**
 * Finds a method by name in this file, matching by receiver type, parameter types, and count.
 *
 * Special handling: Kotlin extension functions compile to static methods with an extra receiver
 * parameter. When navigating from a decompiled JAR, we need to check if a static method with N+1
 * parameters corresponds to a Kotlin extension function with N parameters.
 */
fun PsiFile.findMethodByName(name: String, originalMethod: PsiMethod): PsiElement? {
  // Try Java methods first - match by parameter types
  val javaMethods = PsiTreeUtil.findChildrenOfType(this, PsiMethod::class.java)
  javaMethods
    .find { it.name == name && it.parametersMatchExactly(originalMethod) }
    ?.let {
      return it
    }

  // Try Kotlin functions (including extension functions)
  val ktFunctions = PsiTreeUtil.findChildrenOfType(this, KtFunction::class.java)
  val originalParamCount = originalMethod.parameterList.parametersCount

  // Check if this is a static method that was compiled from a Kotlin extension function
  // Extension functions have receiver + params, static methods have receiver as first param
  if (originalMethod.hasModifierProperty(PsiModifier.STATIC) && originalParamCount > 0) {
    val extensionFunctionParamCount = originalParamCount - 1

    // Try to match receiver type and parameter types
    val firstParamType =
      originalMethod.parameterList.parameters.firstOrNull()?.type?.presentableText
    ktFunctions
      .find { function ->
        function.name == name &&
          function.receiverTypeReference != null &&
          function.valueParameters.size == extensionFunctionParamCount &&
          firstParamType != null &&
          function.receiverTypeReference?.text?.contains(firstParamType) == true
      }
      ?.let {
        return it
      }

    // Fall back to just matching receiver existence and parameter count
    ktFunctions
      .find { function ->
        function.name == name &&
          function.receiverTypeReference != null &&
          function.valueParameters.size == extensionFunctionParamCount
      }
      ?.let {
        return it
      }
  }

  // Try regular function match by parameter count
  ktFunctions
    .find { function ->
      function.name == name &&
        function.receiverTypeReference == null &&
        function.valueParameters.size == originalParamCount
    }
    ?.let {
      return it
    }

  // Fall back to parameter count match for Java methods
  javaMethods
    .find { it.name == name && it.parametersMatch(originalMethod) }
    ?.let {
      return it
    }

  // Last resort: just name match
  return javaMethods.find { it.name == name } ?: ktFunctions.find { it.name == name }
}

/** Checks if this method has the same parameter count as another method. */
fun PsiMethod.parametersMatch(other: PsiMethod): Boolean {
  return parameterList.parametersCount == other.parameterList.parametersCount
}

/**
 * Checks if this method has the exact same parameter types as another method. Uses JVM method
 * signatures for accurate comparison that handles generics, arrays, etc.
 */
fun PsiMethod.parametersMatchExactly(other: PsiMethod): Boolean {
  // Use IntelliJ's MethodSignatureUtil for robust signature comparison
  // This handles generics, arrays, varargs, and all edge cases correctly
  val thisSignature =
    MethodSignatureUtil.createMethodSignature(
      name,
      parameterList,
      typeParameterList,
      com.intellij.psi.PsiSubstitutor.EMPTY,
      false,
    )
  val otherSignature =
    MethodSignatureUtil.createMethodSignature(
      other.name,
      other.parameterList,
      other.typeParameterList,
      com.intellij.psi.PsiSubstitutor.EMPTY,
      false,
    )

  return MethodSignatureUtil.areSignaturesEqual(thisSignature, otherSignature)
}

/**
 * Finds a Kotlin function by name in this file.
 *
 * Matches by receiver type, parameter types, and parameter count to avoid ambiguity when multiple
 * functions with the same name exist (overloads).
 */
fun PsiFile.findFunctionByName(name: String, originalFunction: KtFunction? = null): PsiElement? {
  val ktFunctions = PsiTreeUtil.findChildrenOfType(this, KtFunction::class.java)
  val candidates = ktFunctions.filter { it.name == name }

  // If no original function to match against, return first match
  if (originalFunction == null || candidates.size <= 1) {
    return candidates.firstOrNull()
  }

  // Try to match extension function receiver type AND parameter types
  val originalReceiverType = originalFunction.receiverTypeReference?.text
  if (originalReceiverType != null) {
    // Look for extension function with matching receiver and parameters
    val matchingExtension =
      candidates.find { candidate ->
        candidate.receiverTypeReference?.text == originalReceiverType &&
          candidate.parametersMatchKotlin(originalFunction)
      }
    if (matchingExtension != null) {
      return matchingExtension
    }

    // Fall back to just receiver type match
    val matchingReceiverOnly =
      candidates.find { it.receiverTypeReference?.text == originalReceiverType }
    if (matchingReceiverOnly != null) {
      return matchingReceiverOnly
    }
  }

  // Try to match parameter types for non-extension functions
  val matchingParams =
    candidates.find {
      it.receiverTypeReference == null && it.parametersMatchKotlin(originalFunction)
    }
  if (matchingParams != null) {
    return matchingParams
  }

  // Try to match parameter counts as a fallback
  val originalParamCount = originalFunction.valueParameters.size
  val matchingParamCount = candidates.find { it.valueParameters.size == originalParamCount }
  if (matchingParamCount != null) {
    return matchingParamCount
  }

  // Fall back to first match
  return candidates.firstOrNull()
}

/**
 * Checks if this Kotlin function has the same parameter types as another Kotlin function.
 *
 * For Kotlin functions, we compare the rendered type strings which includes nullability, generics,
 * and other type information. This is more reliable than text-based matching.
 */
fun KtFunction.parametersMatchKotlin(other: KtFunction): Boolean {
  val thisParams = valueParameters
  val otherParams = other.valueParameters

  if (thisParams.size != otherParams.size) {
    return false
  }

  return thisParams.zip(otherParams).all { (thisParam, otherParam) ->
    val thisType = thisParam.typeReference?.text?.trim()
    val otherType = otherParam.typeReference?.text?.trim()

    if (thisType == null || otherType == null) {
      // If either type is missing, match by position only
      return@all true
    }

    // Match by exact type text (includes nullability, generics, etc.)
    if (thisType == otherType) {
      return@all true
    }

    // Fall back to matching simple names (without package qualifiers)
    // This handles cases where one is fully qualified and the other isn't
    val thisSimpleName = thisType.substringAfterLast('.').substringBefore('<')
    val otherSimpleName = otherType.substringAfterLast('.').substringBefore('<')
    thisSimpleName == otherSimpleName
  }
}

/**
 * Finds a field or property by name in this file (supports both Java fields and Kotlin properties).
 */
fun PsiFile.findFieldByName(name: String): PsiElement? {
  // Try Java fields
  PsiTreeUtil.findChildrenOfType(this, PsiField::class.java)
    .find { it.name == name }
    ?.let {
      return it
    }

  // Try Kotlin properties
  PsiTreeUtil.findChildrenOfType(this, KtProperty::class.java)
    .find { it.name == name }
    ?.let {
      return it
    }

  return null
}

/** Finds a Kotlin property by name in this file. */
fun PsiFile.findPropertyByName(name: String): PsiElement? {
  val ktProperties = PsiTreeUtil.findChildrenOfType(this, KtProperty::class.java)
  return ktProperties.find { it.name == name }
}

/** Finds any named element by name in this file. */
fun PsiFile.findNamedElementByName(name: String): PsiElement? {
  val namedElements = PsiTreeUtil.findChildrenOfType(this, PsiNamedElement::class.java)
  return namedElements.find { it.name == name }
}

/**
 * Finds an Android resource in this XML file by matching the "name" attribute. This is used for
 * navigating to specific string, color, dimen resources, etc.
 */
fun PsiFile.findXmlResourceByName(targetTag: XmlTag): PsiElement? {
  if (this !is XmlFile) {
    return null
  }

  // Get the resource name from the target tag's "name" attribute
  val resourceName = targetTag.getAttributeValue("name") ?: return null

  // Search all XML tags in the file for a matching name attribute
  val allTags = PsiTreeUtil.findChildrenOfType(this, XmlTag::class.java)
  return allTags.find { tag -> tag.getAttributeValue("name") == resourceName }
}

/** Finds the element in the source file that corresponds to this target element from a JAR. */
fun PsiElement.findCorrespondingElement(sourceFile: PsiFile): PsiElement? {
  val targetName = getElementName() ?: return null

  return when (this) {
    // Java elements
    is PsiClass -> sourceFile.findClassByName(targetName)
    is PsiMethod -> sourceFile.findMethodByName(targetName, this)
    is PsiField -> sourceFile.findFieldByName(targetName)
    // Kotlin elements (from decompiled)
    is KtClass -> sourceFile.findClassByName(targetName)
    is KtFunction -> sourceFile.findFunctionByName(targetName, this)
    is KtProperty -> sourceFile.findPropertyByName(targetName)
    // XML elements (Android resources)
    is XmlTag -> sourceFile.findXmlResourceByName(this)
    else -> sourceFile.findNamedElementByName(targetName)
  }
}
