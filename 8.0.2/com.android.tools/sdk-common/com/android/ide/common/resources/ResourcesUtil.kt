/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmName("ResourcesUtil")
package com.android.ide.common.resources

import com.android.SdkConstants
import com.android.SdkConstants.DOT_9PNG
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource
import com.android.ide.common.util.PathString
import com.android.ide.common.util.toPathString
import com.android.resources.ResourceType
import com.android.utils.SdkUtils
import java.io.File
import java.util.Collections
import java.util.IdentityHashMap
import java.util.function.Consumer

private val RESOURCE_PROTOCOLS = arrayOf("apk", "jar", "file")

/**
 * Replicates the key flattening done by AAPT. If the passed key contains '.', '-' or ':', they
 * will be replaced by '_' and a a new [String] returned. If none of those characters are
 * contained, the same [String] passed as input will be returned.
 *
 * Please keep a few things in mind when using this method:
 * - The input string should be a valid resource name to begin with, otherwise the output may not be valid.
 * - Recent versions of AGP generate R classes themselves, not by invoking aapt2.
 * - When dealing with attrs, handle the attr name and package name separately i.e. split `"android:color"` into two parts before calling
 *   this function. This is important for correctly supporting resource namespaces in the future.
 *
 * @see ValueResourceNameValidator
 * @see FileResourceNameValidator
 */
fun resourceNameToFieldName(resourceName: String): String {
  var i = 0
  val n = resourceName.length
  while (i < n) {
    var c = resourceName[i]
    if (isInvalidResourceFieldNameCharacter(c)) {
      // We found one instance that we need to replace. Allocate the buffer, copy everything up to this point and start replacing.
      val buffer = CharArray(resourceName.length)
      resourceName.toCharArray(buffer, 0, 0, i)
      buffer[i] = '_'
      for (j in i + 1 until n) {
        c = resourceName[j]
        buffer[j] = if (isInvalidResourceFieldNameCharacter(c)) '_' else c
      }
      return String(buffer)
    }
    i++
  }

  return resourceName
}

/**
 * Checks if the given character should be replace by an underscore when computing the R class field name.
 *
 * @see resourceNameToFieldName
 */
fun isInvalidResourceFieldNameCharacter(c: Char): Boolean {
  return c == ':' || c == '.' || c == '-'
}

/**
 * Returns the resource name that a file with the given `fileName` declares.
 *
 * The returned string is not guaranteed to be a valid resource name, it should be checked by [FileResourceNameValidator] before being used.
 * If the resource type is known, it's preferable to validate the full filename (including extension) first.
 */
fun fileNameToResourceName(fileName: String): String {
  val lastExtension = fileName.lastIndexOf('.')
  return when {
    lastExtension <= 0 -> fileName
    fileName.endsWith(DOT_9PNG, ignoreCase = true) -> {
      if (fileName.length > DOT_9PNG.length) fileName.substring(0, fileName.length - DOT_9PNG.length) else fileName
    }
    else -> fileName.substring(0, lastExtension)
  }
}

/**
 * Finds unused resources in provided resources collection. Marks all used resources as 'reachable'
 * in original collection.
 *
 * @param rootsConsumer function to consume root resources once they are computed.
 */
fun findUnusedResources(
  resources: List<Resource>,
  rootsConsumer: Consumer<List<Resource>>
) = findUnusedResources(resources) { rootsConsumer.accept(it) }

/**
 * Finds unused resources in provided resources collection. Marks all used resources as 'reachable'
 * in original collection.
 *
 * @param rootsConsumer function to consume root resources once they are computed.
 */
fun findUnusedResources(
  resources: List<Resource>,
  rootsConsumer: (List<Resource>) -> Unit
): List<Resource> {
  val seen = Collections.newSetFromMap(IdentityHashMap<Resource, Boolean>())
  fun visit(resource: Resource) {
    if (seen.contains(resource)) {
      return
    }
    seen += resource
    resource.isReachable = true
    resource.references?.forEach { visit(it) }
  }

  val roots = resources.filter { it.isReachable || it.isKeep || it.isPublic }
  rootsConsumer(roots)
  roots.forEach { visit(it) }

  return resources.asSequence()
    .filterNot { it.isReachable }
    .filter { it.type != ResourceType.PUBLIC }
    // Styles not yet handled correctly: don't mark as unused
    .filter { it.type != ResourceType.ATTR && it.type != ResourceType.STYLEABLE }
    // Don't flag known service keys read by library
    .filterNot { SdkUtils.isServiceKey(it.name) }
    .toList()
}

/**
 * Converts a file resource path from [String] to [PathString]. The supported formats:
 * - file path, e.g. "/foo/bar/res/layout/my_layout.xml"
 * - file URL, e.g. "file:///foo/bar/res/layout/my_layout.xml"
 * - URL of a zipped element inside an APK file, e.g. "apk:///foo/bar/res.apk!/res/layout/my_layout.xml"
 *
 * @param resourcePath the file resource path to convert
 * @return the converted resource path, or null if the `resourcePath` doesn't point to a file resource
 */
fun toFileResourcePathString(resourcePath: String): PathString? {
  for (protocol in RESOURCE_PROTOCOLS) {
    if (resourcePath.startsWith(protocol) && resourcePath.length > protocol.length && resourcePath[protocol.length] == ':') {
      var prefixLength = protocol.length + 1
      if (resourcePath.startsWith("//", prefixLength)) {
        prefixLength += "//".length
      }
      return PathString(protocol, resourcePath.substring(prefixLength))
    }
  }

  val file = File(resourcePath)
  return if (file.isFile) file.toPathString() else null
}

/**
 * Returns the given id without an `@id/` or `@+id` prefix.
 *
 * @see com.android.resources.ResourceUrl.parse
 * @see ValueResourceNameValidator.getErrorText
 */
@Deprecated(
  "Use `ResourceUrl.parse` instead and handle wrong resource type, invalid name, trailing whitespace etc.",
  replaceWith = ReplaceWith("ResourceUrl.parse(id)?.name ?: id", imports = ["com.android.resources.ResourceUrl"])
)
fun stripPrefixFromId(id: String): String {
  return when {
    id.startsWith(SdkConstants.NEW_ID_PREFIX) -> id.substring(SdkConstants.NEW_ID_PREFIX.length)
    id.startsWith(SdkConstants.ID_PREFIX) -> id.substring(SdkConstants.ID_PREFIX.length)
    else -> id
  }
}
