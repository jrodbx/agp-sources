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
import com.android.builder.model.v2.ide.LibraryType
import java.io.File
import java.io.Serializable

/**
 * Implementation of [Library] for serialization via the Tooling API.
 */
data class LibraryImpl(
    override val type: LibraryType,
    override val artifactAddress: String,
    override val artifact: File?,
    override val buildId: String?,
    override val projectPath: String?,
    override val variant: String?,
    override val manifest: File?,
    override val compileJarFiles: List<File>?,
    override val runtimeJarFiles: List<File>?,
    override val resFolder: File?,
    override val resStaticLibrary: File?,
    override val assetsFolder: File?,
    override val jniFolder: File?,
    override val aidlFolder: File?,
    override val renderscriptFolder: File?,
    override val proguardRules: File?,
    override val lintJar: File?,
    override val externalAnnotations: File?,
    override val publicResources: File?,
    override val symbolFile: File?
) : Library, Serializable {
    companion object {
        @JvmStatic
        private val serialVersionUID: Long = 1L
    }
}
