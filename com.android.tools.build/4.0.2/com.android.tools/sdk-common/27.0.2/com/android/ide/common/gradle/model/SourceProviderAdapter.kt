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

@file:JvmName("SourceProviderUtil")

package com.android.ide.common.gradle.model

import com.android.builder.model.SourceProvider
import com.android.ide.common.util.PathString
import com.android.projectmodel.AndroidPathType
import com.android.projectmodel.ConfigAssociation
import com.android.projectmodel.SourceSet
import java.io.File

/**
 * Implementation of [SourceProvider] that obtains its state by converting a [SourceSet]. [PathString]
 * instances in the [SourceSet] will be converted to [File] instances using the given [fileResolver]
 * function. The default function works by calling [PathString.toFile], which will work for any
 * [SourceSet] that was originally obtained by converting a [SourceProvider]. However, callers may
 * also pass alternative converters that recognize a larger set of paths.
 *
 * The resulting adapter will:
 *
 * - Only include paths from the [SourceSet] that have a matching type in the [SourceProvider] interface.
 * - Only include paths that can be converted by the [fileResolver]. When using the default [fileResolver],
 *   this wil omit all paths that do not use the "file://" scheme.
 * - Only include the first Android manifest file, if any.
 *
 * All other paths will be stripped from the result.
 */
class SourceProviderAdapter(
    val sourceSet: SourceSet,
    private val name: String,
    private val fileResolver: (PathString) -> File? = { it.toFile() }
) : SourceProvider {
    override fun getName(): String = name

    override fun getManifestFile(): File =
        sourceSet.manifests.firstOrNull()?.let { fileResolver(it) } ?: File("MissingManifest.xml")

    override fun getJavaDirectories(): MutableCollection<File> =
        convert(sourceSet[AndroidPathType.JAVA])

    override fun getAidlDirectories(): MutableCollection<File> =
        convert(sourceSet[AndroidPathType.AIDL])

    override fun getResourcesDirectories(): MutableCollection<File> =
        convert(sourceSet[AndroidPathType.RESOURCE])

    override fun getRenderscriptDirectories(): MutableCollection<File> =
        convert(sourceSet[AndroidPathType.RENDERSCRIPT])

    override fun getCDirectories(): MutableCollection<File> = convert(sourceSet[AndroidPathType.C])

    override fun getCppDirectories(): MutableCollection<File> =
        convert(sourceSet[AndroidPathType.CPP])

    override fun getAssetsDirectories(): MutableCollection<File> =
        convert(sourceSet[AndroidPathType.ASSETS])

    override fun getJniLibsDirectories(): MutableCollection<File> =
        convert(sourceSet[AndroidPathType.JNI_LIBS])

    override fun getResDirectories(): MutableCollection<File> = convert(sourceSet.resDirectories)

    override fun getShadersDirectories(): MutableCollection<File> =
        convert(sourceSet[AndroidPathType.SHADERS])

    private fun convert(strings: List<PathString>): MutableCollection<File> {
        return strings.mapNotNull { it.toFile() }.toMutableList()
    }
}

fun SourceSet.toSourceProvider(name: String): SourceProviderAdapter =
    SourceProviderAdapter(this, name)

/**
 * Converts a [ConfigAssocation] to a [SourceProvider]. The name of the [SourceProvider] comes
 * from the simple name of the associated [ConfigPath]
 */
fun ConfigAssociation.toSourceProvider() = config.sources.toSourceProvider(path.simpleName)
