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
package com.android.builder.model.v2.ide

import com.android.builder.model.v2.AndroidModel
import java.io.File

/**
 * Represent a variant/module/artifact dependency.
 */
interface Library: AndroidModel {
    /**
     * A Unique key representing the library, and allowing to match it with [GraphItem] instances
     */
    val key: String

    /**
     * The type of the dependency.
     */
    val type: LibraryType

    /**
     * Returns the project info to uniquely identify it (and its variant)
     *
     * Only valid for instances where [type] is [LibraryType.PROJECT]. It is null in other cases.
     */
    val projectInfo: ProjectInfo?

    /**
     * Returns the external library info to uniquely identify it (and its variant)
     *
     * Only valid for instances where [type] is [LibraryType.ANDROID_LIBRARY], or
     * [LibraryType.JAVA_LIBRARY]. It is null in other cases.
     */
    val libraryInfo: LibraryInfo?

    /**
     * The artifact location.
     *
     * Only valid for instances where [type] is [LIBRARY_JAVA]
     */
    val artifact: File?

    /**
     * The location of the manifest file.
     *
     * Only valid for instances where [type] is [LibraryType.ANDROID_LIBRARY]
     */
    val manifest: File?

    /**
     * The list of jar files for compilation.
     *
     * Only valid for instances where [type] is [LibraryType.ANDROID_LIBRARY]
     */
    val compileJarFiles: List<File>?

    /**
     * The list of jar files for runtime/packaging.
     *
     * Only valid for instances where [type] is [LibraryType.ANDROID_LIBRARY]
     */
    val runtimeJarFiles: List<File>?

    /**
     * The android resource folder.
     *
     * The folder may not exist.
     *
     * Only valid for instances where [type] is [LibraryType.ANDROID_LIBRARY]
     */
    val resFolder: File?

    /**
     * The namespaced resources static library (res.apk).
     *
     * Only valid for instances where [type] is [LibraryType.ANDROID_LIBRARY]. This can still be null if the
     * library is not namespaced.
     */
    val resStaticLibrary: File?

    /**
     * The assets folder.
     *
     * The folder may not exist.
     *
     * Only valid for instances where [type] is [LibraryType.ANDROID_LIBRARY]
     */
    val assetsFolder: File?

    /**
     * The jni libraries folder.
     *
     * The folder may not exist.
     *
     * Only valid for instances where [type] is [LibraryType.ANDROID_LIBRARY]
     */
    val jniFolder: File?

    /**
     * The AIDL import folder
     *
     * The folder may not exist.
     *
     * Only valid for instances where [type] is [LibraryType.ANDROID_LIBRARY]
     */
    val aidlFolder: File?

    /**
     * The RenderScript import folder
     *
     * The folder may not exist.
     *
     * Only valid for instances where [type] is [LibraryType.ANDROID_LIBRARY]
     */
    val renderscriptFolder: File?

    /**
     * The proguard file rule.
     *
     * The file may not exist.
     *
     * Only valid for instances where [type] is [LibraryType.ANDROID_LIBRARY]
     */
    val proguardRules: File?

    /**
     * The jar containing custom lint checks
     *
     * The file may not exist.
     *
     * Only valid for instances where [type] is [LibraryType.ANDROID_LIBRARY]
     */
    val lintJar: File?

    /**
     * the zip file with external annotations
     *
     * The file may not exist.
     *
     * Only valid for instances where [type] is [LibraryType.ANDROID_LIBRARY]
     */
    val externalAnnotations: File?

    /**
     * The file listing the public resources
     *
     * The file may not exist.
     *
     * Only valid for instances where [type] is [LibraryType.ANDROID_LIBRARY]
     */
    val publicResources: File?

    /**
     * The symbol list file
     *
     * The file may not exist.
     *
     * Only valid for instances where [type] is [LibraryType.ANDROID_LIBRARY]
     */
    val symbolFile: File?
}
