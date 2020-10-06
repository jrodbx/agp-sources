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

interface LintModelSourceProvider {
    val manifestFile: File
    val javaDirectories: Collection<File>
    val resDirectories: Collection<File>
    val assetsDirectories: Collection<File>

    /** Are the sources from this source provider specific to tests (of any type) ? */
    fun isTest(): Boolean = isUnitTest() or isInstrumentationTest()

    /** Are the sources from this source provider specific to unit tests? */
    fun isUnitTest(): Boolean

    /** Are the sources from this source provider specific to instrumentation tests? */
    fun isInstrumentationTest(): Boolean

    /** Are the sources from this source provider in a debug-specific source set? */
    fun isDebugOnly(): Boolean
}

class DefaultLintModelSourceProvider(
    override val manifestFile: File,
    override val javaDirectories: Collection<File>,
    override val resDirectories: Collection<File>,
    override val assetsDirectories: Collection<File>,
    private val debugOnly: Boolean,
    private val unitTestOnly: Boolean,
    private val instrumentationTestOnly: Boolean
) : LintModelSourceProvider {
    override fun isUnitTest(): Boolean = unitTestOnly
    override fun isInstrumentationTest(): Boolean = instrumentationTestOnly
    override fun isDebugOnly(): Boolean = debugOnly

    override fun toString(): String {
        return manifestFile.parentFile?.path ?: "?"
    }
}
