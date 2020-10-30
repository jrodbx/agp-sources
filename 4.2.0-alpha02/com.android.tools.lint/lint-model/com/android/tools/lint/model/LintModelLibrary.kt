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

package com.android.tools.lint.model

import java.io.File

/** A library */
interface LintModelLibrary : Comparable<LintModelLibrary> {
    /**
     * Whether this library is provided ("compileOnly" in Gradle), meaning that it
     * should not be packed with the app or library; it will be provided in the
     * running container
     */
    val provided: Boolean

    /**
     * Whether this library is "skipped"; an example of this is the R class that is
     * provided to other modules but not to the runtime
     */
    val skipped: Boolean

    /**
     * Returns the artifact address in a unique way.
     *
     * This is either a module path for sub-modules (with optional variant name), or a maven
     * coordinate for external dependencies.
     */
    val artifactAddress: String

    /** The actual resolved Maven coordinates of this library */
    val resolvedCoordinates: LintModelMavenName

    /** The location of an unzipped AAR or the corresponding Gradle project */
    val folder: File?

    val lintJar: File?

    override fun compareTo(other: LintModelLibrary): Int {
        return resolvedCoordinates.compareTo(other.resolvedCoordinates)
    }
}

interface LintModelModuleLibrary : LintModelLibrary {
    /**
     * The path to a local project represented in terms of the current build system.
     *
     * Note: Currently, when created from Gradle models it does not support composite builds.
     */
    val projectPath: String
}

interface LintModelExternalLibrary : LintModelLibrary {
    /** List of jar files in the library. Never empty. */
    val jarFiles: List<File>
}

interface LintModelAndroidLibrary : LintModelExternalLibrary {
    val manifest: File
    override val folder: File
    val resFolder: File
    val assetsFolder: File
    val publicResources: File
    val symbolFile: File
    val externalAnnotations: File
    val proguardRules: File
}

interface LintModelJavaLibrary : LintModelExternalLibrary

// Default implementations

abstract class DefaultLintModelLibrary : LintModelLibrary {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return artifactAddress == (other as? LintModelLibrary)?.artifactAddress
    }

    override fun hashCode(): Int {
        return artifactAddress.hashCode()
    }
}

class DefaultLintModelModuleLibrary(
    override val artifactAddress: String,
    override val projectPath: String,
    override val resolvedCoordinates: LintModelMavenName,
    override val folder: File?,
    override val lintJar: File?,
    override val provided: Boolean,
    override val skipped: Boolean
) : DefaultLintModelLibrary(), LintModelModuleLibrary {
    override fun toString(): String = "LocalLibrary(${projectPath})"
}

class DefaultLintModelAndroidLibrary(
    override val jarFiles: List<File>,
    override val artifactAddress: String,
    override val manifest: File,
    override val folder: File,
    override val resFolder: File,
    override val assetsFolder: File,
    override val lintJar: File,
    override val publicResources: File,
    override val symbolFile: File,
    override val externalAnnotations: File,
    override val proguardRules: File,
    override val provided: Boolean,
    override val skipped: Boolean,
    override val resolvedCoordinates: LintModelMavenName
) : DefaultLintModelLibrary(), LintModelAndroidLibrary {
    override fun toString(): String = "AndroidLibrary(${resolvedCoordinates})"
}

class DefaultLintModelJavaLibrary(
    override val artifactAddress: String,
    override val folder: File?,
    override val jarFiles: List<File>,
    override val resolvedCoordinates: LintModelMavenName,
    override val provided: Boolean,
    override val skipped: Boolean
) : DefaultLintModelLibrary(), LintModelJavaLibrary {
    override val lintJar: File? = null
    override fun toString(): String = "JavaLibrary(${resolvedCoordinates})"
}
