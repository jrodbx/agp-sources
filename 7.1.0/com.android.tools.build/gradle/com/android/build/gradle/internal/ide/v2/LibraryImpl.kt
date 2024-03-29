/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.ide.v2

import com.android.builder.model.v2.ide.Library
import com.android.builder.model.v2.ide.LibraryInfo
import com.android.builder.model.v2.ide.LibraryType
import com.android.builder.model.v2.ide.ProjectInfo
import java.io.File
import java.io.Serializable

/**
 * Implementation of [Library] for serialization via the Tooling API.
 */
data class LibraryImpl(
    override val key: String,
    override val type: LibraryType,
    override val projectInfo: ProjectInfo? = null,
    override val libraryInfo: LibraryInfo? = null,
    override val artifact: File? = null,
    override val manifest: File? = null,
    override val compileJarFiles: List<File>? = null,
    override val runtimeJarFiles: List<File>? = null,
    override val resFolder: File? = null,
    override val resStaticLibrary: File? = null,
    override val assetsFolder: File? = null,
    override val jniFolder: File? = null,
    override val aidlFolder: File? = null,
    override val renderscriptFolder: File? = null,
    override val proguardRules: File? = null,
    override val lintJar: File? = null,
    override val externalAnnotations: File? = null,
    override val publicResources: File? = null,
    override val symbolFile: File? = null
) : Library, Serializable {
    companion object {
        @JvmStatic
        private val serialVersionUID: Long = 1L
    }
}
