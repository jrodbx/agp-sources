/*
 * Copyright (C) 2018 The Android Open Source Project
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
@file:JvmName("GradleModelConverterUtil")

package com.android.ide.common.gradle.model

import com.android.ide.common.gradle.model.impl.ModelCache
import com.android.ide.common.util.PathString
import com.android.ide.common.util.toPathString
import com.android.projectmodel.DynamicResourceValue
import com.android.projectmodel.ExternalLibrary
import com.android.projectmodel.Library
import com.android.projectmodel.ProjectLibrary
import com.android.projectmodel.RecursiveResourceFolder
import com.android.resources.ResourceType
import com.google.common.collect.ImmutableMap

// This file contains utilities for converting Gradle model types (from builder-model) into project model types.

class GradleModelConverter(val project: IdeAndroidProject) {

  /**
   * Converts the given [IdeLibrary] into a [Library]. Returns null if the given library is badly formed.
   */
  fun convert(library: IdeLibrary): Library = convertLibrary(library)
}

fun classFieldsToDynamicResourceValues(classFields: Map<String, IdeClassField>): Map<String, DynamicResourceValue> {
    val result = HashMap<String, DynamicResourceValue>()
    for (field in classFields.values) {
        val resourceType = ResourceType.fromClassName(field.type)
        if (resourceType != null) {
            result[field.name] = DynamicResourceValue(resourceType, field.value)
        }
    }
    return ImmutableMap.copyOf(result)
}

/**
 * Converts a builder-model [IdeLibrary] into a [Library]. Returns null
 * if the input is invalid.
 */
fun convertLibrary(builderModelLibrary: IdeLibrary): Library =
  with(builderModelLibrary) {
    when (type) {
      IdeLibrary.LibraryType.LIBRARY_ANDROID -> ExternalLibrary(
        address = artifactAddress,
        location = artifact.toPathString(),
        manifestFile = PathString(manifest),
        classJars = listOf(PathString(jarFile)),
        dependencyJars = localJars.map(::PathString),
        resFolder = RecursiveResourceFolder(PathString(resFolder)),
        symbolFile = PathString(symbolFile),
                resApkFile = resStaticLibrary?.let(::PathString)
            )
            IdeLibrary.LibraryType.LIBRARY_JAVA -> ExternalLibrary(
                address = artifactAddress,
                classJars = listOf(artifact.toPathString())
            )
            IdeLibrary.LibraryType.LIBRARY_MODULE -> {
                val path = projectPath
                if (path == null) error("LIBRARY_MODULE cannot have null projectPath")
                else ProjectLibrary(
                    address = artifactAddress,
                    projectName = path,
                    variant = variant ?: ""
                )
            }
        }
    }
