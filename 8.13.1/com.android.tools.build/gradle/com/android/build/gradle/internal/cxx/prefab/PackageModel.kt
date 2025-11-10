/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.prefab

import com.google.gson.annotations.SerializedName

/*************************************************************************************
 * These classes represent the JSon used inside a Prefab package to represent modules,
 * libraries, etc.
 * The following metadata classes are copied from
 * https://github.com/google/prefab/tree/master/api/src/main/kotlin/com/google/prefab/api
 *
 * The metadata is stable for a given schema version, but if we need to update to a
 * newer schema version we'll need to update these.
 ************************************************************************************/

/**
 * The v1 prefab.json schema.
 *
 * @property[name] The name of the module.
 * @property[schemaVersion] The version of the schema.
 * @property[dependencies] A list of other packages required by this package.
 * @property[version] The package version. For compatibility with CMake, this
 * *must* be formatted as major[.minor[.patch[.tweak]]] with all components
 * being numeric, even if that does not match the package's native version.
 */
data class PackageMetadataV1(
    val name: String,
    @SerializedName("schema_version")
    val schemaVersion: Int,
    val dependencies: List<String>,
    val version: String? = null
)

/**
 * The module metadata that is overridable per-platform in module.json.
 *
 * @property[exportLibraries] The list of libraries other than the library
 * described by this module that must be linked by users of this module.
 * @property[libraryName] The name (without the file extension) of the library
 * file. The file extension is automatically detected based on the contents of
 * the directory.
 */
data class PlatformSpecificModuleMetadataV1(
    @SerializedName("export_libraries")
    val exportLibraries: List<String>? = null,
    @SerializedName("library_name")
    val libraryName: String? = null
)

/**
 * The v1 module.json schema.
 *
 * @property[exportLibraries] The list of libraries other than the library
 * described by this module that must be linked by users of this module.
 * @property[libraryName] The name (without the file extension) of the library
 * file. The file extension is automatically detected based on the contents of
 * the directory.
 * @property[android] Android specific values that override the main values if
 * present.
 */
data class ModuleMetadataV1(
    @SerializedName("export_libraries")
    val exportLibraries: List<String>,
    @SerializedName("library_name")
    val libraryName: String? = null,
    // Allowing per-platform overrides before we support more than a single
    // platform might seem like overkill, but this makes it easier to maintain
    // compatibility for old packages when new platforms are added. If we added
    // this in schema version 2, there would be no way to reliably migrate v1
    // packages because we wouldn't know whether the exported libraries were
    // Android specific or not.
    val android: PlatformSpecificModuleMetadataV1 =
        PlatformSpecificModuleMetadataV1(
            null,
            null
        )
)

/**
 * The Android abi.json schema.
 *
 * @property[abi] The ABI name of the described library. These names match the tag field for
 * [com.android.build.gradle.internal.core.Abi].
 * @property[api] The minimum OS version supported by the library. i.e. the
 * library's `minSdkVersion`.
 * @property[ndk] The major version of the NDK that this library was built with.
 * @property[stl] The STL that this library was built with.
 * @property[static] If true then the library is .a, if false then .so.
 */
data class AndroidAbiMetadata(
    val abi: String,
    val api: Int,
    val ndk: Int,
    val stl: String,
    val static: Boolean?
)
