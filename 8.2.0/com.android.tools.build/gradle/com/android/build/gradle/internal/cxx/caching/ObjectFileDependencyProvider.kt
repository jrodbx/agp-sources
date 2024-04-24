/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.caching

import com.android.build.gradle.internal.cxx.ninja.NinjaDepsInfo

/**
 * Interface defines an abstract provider of dependencies for a
 * given .o [objectFile].
 *
 * This is mainly a thin wrapper over [NinjaDepsInfo] and consists
 * of information from .ninja_deps file.
 *
 * The semantics of this interface are that strings are directly
 * and literally from the source of truth (the literal values from
 * .ninja_deps). It's up to the caller to supply and consume strings
 * in this format. For this reason, [String] is used instead of
 * [File].
 */
interface ObjectFileDependencyProvider {

    /**
     * Return the dependencies for [objectFile]. Typically, this is:
     * - Source .c or .cpp file
     * - Followed by implied #include files
     *
     * When [objectFile] isn't known by the source of truth, this
     * function will return null.
     *
     * Empty list is reserved for the case where dependencies are
     * known but they are empty. But this probably won't happen in
     * normal operation.
     */
    fun dependencies(objectFile: String) : List<String>?
}

/**
 * A dependency provider that always returns null. This is for the case
 * that .ninja_deps doesn't exist yet (it won't exist before the first
 * build).
 */
private val NOP_SOURCE_FILE_DEPENDENCY_PROVIDER =
    object : ObjectFileDependencyProvider {
        override fun dependencies(objectFile: String) : List<String>? = null
    }

/**
 * Implementation of [ObjectFileDependencyProvider] over [NinjaDepsInfo].
 */
private class NinjaDepsObjectFileDependencyProvider(
    private val ninjaDeps : NinjaDepsInfo) : ObjectFileDependencyProvider {

    override fun dependencies(objectFile: String): List<String>? {
        return ninjaDeps.getDependencies(objectFile)
    }
}
