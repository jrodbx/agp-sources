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

/**
 * Output mode for dexing.
 *
 * Each mode should map to [com.android.tools.r8.OutputMode]. It may also provide additional
 * information (e.g., [DexFilePerClassFile.getDexOutputRelativePathsOfClassFile] is needed for
 * incremental dexing).
 */
interface DexOutputMode {
    val r8OutputMode: OutputMode
}

object DexFilePerClassFile : DexOutputMode {

    override val r8OutputMode
        get() = OutputMode.DexFilePerClassFile

    /**
     * Returns the Unix-style relative paths of all the possible dex outputs under the dex output
     * directory or jar when D8 processes the class file with the given relative path.
     *
     * (If the given relative path is not in Unix style, it will be converted to that first.)
     */
    fun getDexOutputRelativePathsOfClassFile(classFileRelativePath: String): Set<String> {
        // Given a class file, `OutputMode.DexFilePerClassFile` will produce 1 dex file + additional
        // synthetic files if necessary.
        // For example, given the following class files:
        //   - com/example/NormalClass.class
        //   - com/example/NormalClass$InnerClass.class
        //   - com/example/InterfaceWithDefaultMethod.class
        // `OutputMode.DexFilePerClassFile` will produce the following output files:
        //   - com/example/NormalClass.dex
        //   - com/example/NormalClass$InnerClass.dex
        //   - com/example/InterfaceWithDefaultMethod.dex (this dex file contains the
        //     `com/example/InterfaceWithDefaultMethod` class and possibly the synthetic
        //     `com/example/InterfaceWithDefaultMethod$-CC` class if desugaring requires it)
        //   - Additional synthetic files if necessary
        // Note that `OutputMode.DexFilePerClass` (`*PerClass`, not `*PerClassFile`) will produce 2
        // separate dex files for `com/example/InterfaceWithDefaultMethod` and
        // `com/example/InterfaceWithDefaultMethod$-CC`. That's why it's simpler to use
        // `OutputMode.DexFilePerClassFile`.
        return setOf(
            // There is currently 1 output file / class file, but there may be more in the future.
            ClassFileEntry.withDexExtension(File(classFileRelativePath).invariantSeparatorsPath)
        )
    }

    /**
     * Returns the global synthetics output relative path of a class file.
     */
    fun getGlobalOutputRelativePathOfClassFile(classFileRelativePath: String): String {
        return classFileRelativePath.substring(
            0, classFileRelativePath.length - SdkConstants.DOT_CLASS.length) + globalSyntheticsFileExtension
    }
}

object DexIndexed : DexOutputMode {

    override val r8OutputMode
        get() = OutputMode.DexIndexed
}
