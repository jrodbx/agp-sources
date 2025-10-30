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

package com.android.builder.dexing

import java.io.File
import java.nio.file.Path
import java.util.stream.Stream

/**
 * An abstract dex archive builder that converts input class files to dex files that are written to
 * dex archive. This class contains the logic for reading the class files from the input,
 * [ClassFileInput], and writing the output to a [DexArchive]. Implementation of conversion from the
 * class files to dex files is left to the sub-classes. To trigger the conversion, create an
 * instance of this class, and invoke [convert].
 */
abstract class DexArchiveBuilder {

    /**
     * Converts the specified input, and writes it to the output dex archive. If dex archive does
     * not exist, it will be created. If it exists, entries will be added or replaced.
     *
     * @param input a [Stream] of input class files
     * @param dexOutput the path to the directory or jar containing output dex files
     * @param globalSyntheticsOutput the path to the directory containing output global synthetics files
     * @param desugarGraphUpdater the dependency graph for desugaring to be updated. It could be
     *     `null` if the dependency graph is not required or is computed by the Android Gradle
     *     plugin.
     */
    @Throws(DexArchiveBuilderException::class)
    abstract fun convert(
        input: Stream<ClassFileEntry>,
        dexOutput: Path,
        globalSyntheticsOutput: Path?,
        desugarGraphUpdater: DependencyGraphUpdater<File>? = null
    )

    companion object {

        /** Creates an instance that is using d8 to convert class files to dex files.  */
        @JvmStatic
        fun createD8DexBuilder(dexParams: DexParameters): DexArchiveBuilder {
            return D8DexArchiveBuilder(dexParams)
        }
    }
}
