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
 *
 * @since 4.2
 */
interface Library: AndroidModel {
    /**
     * The type of the dependency.
     */
    val type: LibraryType

    /**
     * The artifact address in a unique way.
     *
     * This is either a module path for sub-modules (with optional variant name), or a maven
     * coordinate for external dependencies.
     */
    val artifactAddress: String

    /**
     * The artifact location.
     *
     * Only valid for instances where [type] is [LIBRARY_JAVA]
     */
    val artifact: File?

    /**
     * Returns the build id.
     *
     * Only valid for instances where [type] is [LIBRARY_MODULE]. Null in this case indicates
     * the root build.
     *
     * @return the build id or null.
     */
    val buildId: String?

    /**
     * The gradle path.
     *
     * Only valid for instances where [type] is [LIBRARY_MODULE]
     */
    val projectPath: String?

    /**
     * On optional variant name if the consumed artifact of the library is associated
     * to one.
     *
     * Only valid for instances where [type] is [LIBRARY_MODULE]
     */
    val variant: String?

    /**
     * The location of the manifest file.
     *
     * Only valid for instances where [type] is [LIBRARY_ANDROID]
     */
    val manifest: File?

    /**
     * The list of jar files for compilation.
     *
     * Only valid for instances where [type] is [LIBRARY_ANDROID]
     */
    val compileJarFiles: List<File>?

    /**
     * The list of jar files for runtime/packaging.
     *
     * Only valid for instances where [type] is [LIBRARY_ANDROID]
     */
    val runtimeJarFiles: List<File>?

    /**
     * The android resource folder.
     *
     * The folder may not exist.
     *
     * Only valid for instances where [type] is [LIBRARY_ANDROID]
     */
    val resFolder: File?

    /**
     * The namespaced resources static library (res.apk).
     *
     * Only valid for instances where [type] is [LIBRARY_ANDROID]. This can still be null if the
     * library is not namespaced.
     */
    val resStaticLibrary: File?

    /**
     * The assets folder.
     *
     * The folder may not exist.
     *
     * Only valid for instances where [type] is [LIBRARY_ANDROID]
     */
    val assetsFolder: File?

    /**
     * The jni libraries folder.
     *
     * The folder may not exist.
     *
     * Only valid for instances where [type] is [LIBRARY_ANDROID]
     */
    val jniFolder: File?

    /**
     * The AIDL import folder
     *
     * The folder may not exist.
     *
     * Only valid for instances where [type] is [LIBRARY_ANDROID]
     */
    val aidlFolder: File?

    /**
     * The RenderScript import folder
     *
     * The folder may not exist.
     *
     * Only valid for instances where [type] is [LIBRARY_ANDROID]
     */
    val renderscriptFolder: File?

    /**
     * The proguard file rule.
     *
     * The file may not exist.
     *
     * Only valid for instances where [type] is [LIBRARY_ANDROID]
     */
    val proguardRules: File?

    /**
     * The jar containing custom lint checks
     *
     * The file may not exist.
     *
     * Only valid for instances where [type] is [LIBRARY_ANDROID]
     */
    val lintJar: File?

    /**
     * the zip file with external annotations
     *
     * The file may not exist.
     *
     * Only valid for instances where [type] is [LIBRARY_ANDROID]
     */
    val externalAnnotations: File?

    /**
     * The file listing the public resources
     *
     * The file may not exist.
     *
     * Only valid for instances where [type] is [LIBRARY_ANDROID]
     */
    val publicResources: File?

    /**
     * The symbol list file
     *
     * The file may not exist.
     *
     * Only valid for instances where [type] is [LIBRARY_ANDROID]
     */
    val symbolFile: File?
}
