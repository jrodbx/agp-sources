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

package com.android.builder.dexing

import com.android.SdkConstants
import com.android.tools.r8.OutputMode
import java.io.File

/** Output mode for dexing. */
interface DexOutputMode {
    val outputMode: OutputMode
}

/**
 * Given a class file, [OutputMode.DexFilePerClassFile] will produce 1 dex file + 1 additional
 * global synthetic file if necessary.
 *
 * For example, given `com/example/InterfaceWithDefaultMethod.class`, it will produce
 *   - `com/example/InterfaceWithDefaultMethod.dex` (this
 *   dex file contains the `com/example/InterfaceWithDefaultMethod` class and possibly the synthetic
 *   `com/example/InterfaceWithDefaultMethod$-CC` class if desugaring requires it)
 *   - 1 additional global synthetic file if necessary
 *
 * Note that for incremental dexing purposes, [OutputMode.DexFilePerClassFile] is better than
 * [OutputMode.DexFilePerClass] because in the above example the latter may produce 2 separate dex
 * files `com/example/InterfaceWithDefaultMethod.dex` and
 * `com/example/InterfaceWithDefaultMethod$-CC.dex` given 1 class file.
 */
object DexFilePerClassFile : DexOutputMode {

    override val outputMode
        get() = OutputMode.DexFilePerClassFile

    /**
     * Returns the Unix-style relative path of the *dex* output file under the output directory or
     * jar after D8 processes the class file with the given relative path.
     *
     * (If the given relative path is not in Unix style, it will be converted to that first.)
     */
    fun getDexOutputRelativePath(classFileRelativePath: String): String {
        check(classFileRelativePath.endsWith(SdkConstants.DOT_CLASS)) {
            "Expected .class file but found: $classFileRelativePath"
        }
        return File(classFileRelativePath).invariantSeparatorsPath.removeSuffix(SdkConstants.DOT_CLASS) + SdkConstants.DOT_DEX
    }

    /**
     * Returns the Unix-style relative path of the *global synthetic* output file under the output
     * directory or jar after D8 processes the class file with the given relative path.
     *
     * (If the given relative path is not in Unix style, it will be converted to that first.)
     */
    fun getGlobalSyntheticOutputRelativePath(classFileRelativePath: String): String {
        check(classFileRelativePath.endsWith(SdkConstants.DOT_CLASS)) {
            "Expected .class file but found: $classFileRelativePath"
        }
        return File(classFileRelativePath).invariantSeparatorsPath.removeSuffix(SdkConstants.DOT_CLASS) + globalSyntheticsFileExtension
    }
}

object DexIndexed : DexOutputMode {

    override val outputMode
        get() = OutputMode.DexIndexed
}
