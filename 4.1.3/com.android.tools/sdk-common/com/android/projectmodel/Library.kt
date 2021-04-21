/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.projectmodel

import com.android.ide.common.util.PathString

/**
 * Represents a dependency for a [Variant].
 */
sealed class Library {
    /**
     * Globally unique identifier for this library, assigned by the build system.
     */
    abstract val address: String
}

/**
 * Represents a dependency on an external library. External libraries are folders containing
 * some combination of prebuilt classes, resources, and manifest file. Although android
 * libraries are normally packaged in an AAR file, the actual dependency is on the folder where the
 * AAR would have been extracted by the build system rather than the AAR file itself. If the library
 * came from an AAR file, the build system would extract it somewhere and then provide an instance
 * of [ExternalLibrary] describing the contents and location of the extracted folder.
 */
data class ExternalLibrary(
    override val address: String,

    /**
     * Path to the .aar file on the filesystem, if known and one exists.
     *
     * The IDE doesn't work with AAR files and instead relies on the build system to extract
     * necessary files to disk. Location of the original AAR files is not always known, and some
     * [ExternalLibrary] instances point to folders that never came from an AAR. In such cases, this
     * attribute is null.
     */
    val location: PathString? = null,

    /**
     * Location of the manifest file for this library. This manifest contains sufficient information
     * to understand the library and its contents are intended to be merged into any application
     * that uses the library.
     *
     * Any library that contains resources must either provide a [manifestFile] or a [packageName].
     * Not all libraries include a manifest. For example, some libraries may not contain resources.
     * Other libraries may contain resources but use some other mechanism to inform the build system
     * of the package name. The latter will fill in [packageName] rather than providing a
     * [manifestFile].
     */
    val manifestFile: PathString? = null,

    /**
     * Java package name for the resources in this library.
     */
    val packageName: String? = null,

    /**
     * Path to .jar file(s) containing the library classes. This list will be empty if the library
     * does not include any java classes.
     */
    val classJars: List<PathString> = emptyList(),

    /**
     * Paths to jars that were packaged inside the AAR and are dependencies of it.
     *
     * This used by Gradle when a library depends on a local jar file that has no Maven coordinates,
     * so needs to be packaged together with the AAR to be accessible to client apps.
     */
    val dependencyJars: List<PathString> = emptyList(),

    /**
     * Path to the folder containing unzipped, plain-text, non-namespaced resources. Or null
     * for libraries that contain no resources.
     */
    val resFolder: ResourceFolder? = null,

    /**
     * Path to the symbol file (`R.txt`) containing information necessary to generate the
     * non-namespaced R class for this library. Null if no such file exists.
     */
    val symbolFile: PathString? = null,

    /**
     * Path to the aapt static library (`res.apk`) containing namespaced resources in proto format.
     *
     * This is only known for "AARv2" files, built from namespaced sources.
     */
    val resApkFile: PathString? = null
) : Library() {
    /**
     * Constructs a new [ExternalLibrary] with the given address and all other values set to their defaults. Intended to
     * simplify construction from Java.
     */
    constructor(address: String) : this(address, null)

    override fun toString(): String = printProperties(this, ExternalLibrary(""))

    /**
     * Returns true if this [ExternalLibrary] contributes any resources. Resources may be packaged
     * as either a res.apk file or a res folder.
     */
    @get:JvmName("hasResources")
    val hasResources get() = resApkFile != null || resFolder != null

    /**
     * Returns a copy of the receiver with the given manifest file. Intended to simplify construction from Java.
     */
    fun withManifestFile(path: PathString?) = copy(manifestFile = path)

    /**
     * Returns a copy of the receiver with the given list of java class jars. Intended to simplify construction from Java.
     */
    fun withClassJars(paths: List<PathString>) = copy(classJars = paths)

    /**
     * Returns a copy of the receiver with the given res folder. Intended to simplify construction from Java.
     */
    fun withResFolder(path: ResourceFolder?) = copy(resFolder = path)

    /**
     * Returns a copy of the receiver with the given location. Intended to simplify construction from Java.
     */
    fun withLocation(path: PathString?) = copy(location = path)

    /**
     * Returns a copy of the receiver with the given symbol file. Intended to simplify construction from Java.
     */
    fun withSymbolFile(path: PathString?) = copy(symbolFile = path)

    /**
     * Returns a copy of the receiver with the given [packageName]. Intended to simplify construction from Java.
     */
    fun withPackageName(packageName: String?) = copy(packageName = packageName)

    /**
     * Returns true iff this [Library] contains no files
     */
    fun isEmpty() = this == ExternalLibrary(address = address, packageName = packageName)
}

/**
 * Represents a dependency on another [AndroidSubmodule].
 */
data class ProjectLibrary(
    override val address: String,
    /**
     * Name of the project (matches the project's [AndroidSubmodule.name] attribute).
     */
    val projectName: String,
    /**
     * Variant of the project being depended upon (matches the variant's [Variant.name] attribute).
     */
    val variant: String
) : Library()
