package com.android.aaptcompiler

import com.android.SdkConstants
import javax.xml.stream.events.StartElement

const val SCHEMA_PUBLIC_PREFIX = SdkConstants.URI_PREFIX
const val SCHEMA_PRIVATE_PREFIX = "http://schemas.android.com/apk/prv/res/"
const val SCHEMA_AUTO = SdkConstants.AUTO_URI

// Result of extracting a package name from a namespace URI declaration
data class ExtractedPackage(val packageName: String = "", val isPrivate: Boolean = false)

fun extractPackageFromUri(namespaceUri: String): ExtractedPackage? {
  return when {
    namespaceUri.startsWith(SCHEMA_PUBLIC_PREFIX) -> {
      val packageName = namespaceUri.substring(SCHEMA_PUBLIC_PREFIX.length)
      if (packageName.isEmpty()) {
        return null
      }
      ExtractedPackage(packageName, false)
    }
    namespaceUri.startsWith(SCHEMA_PRIVATE_PREFIX) -> {
      val packageName = namespaceUri.substring(SCHEMA_PRIVATE_PREFIX.length)
      if (packageName.isEmpty()) {
        return null
      }
      ExtractedPackage(packageName, true)
    }
    namespaceUri == SCHEMA_AUTO -> ExtractedPackage("", true)
    else -> null
  }
}

fun resolvePackage(element: StartElement, ref: Reference) {
  val name = ref.name
  val prefix = name.pck!!
  val uri = element.getNamespaceURI(prefix) ?: ""

  val extractedPackage = when {
    prefix.isEmpty() -> ExtractedPackage()
    else -> extractPackageFromUri(uri)
  }

  if (extractedPackage != null) {
    ref.name = ResourceName(extractedPackage.packageName, name.type, name.entry)
    // If the reference was private (i.e. *name) and the namespace is public, the reference should
    // remain private
    ref.isPrivate = ref.isPrivate || extractedPackage.isPrivate
  }
}