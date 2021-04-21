/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.dexing

import com.android.builder.dexing.ClassBucket
import java.io.File
import java.io.Serializable

/** Information required for incremental dexing. */
class IncrementalDexSpec(

    /** The input class files to dex. A class file could be a regular file or a jar entry. */
    val inputClassFiles: ClassBucket,

    /** The path to a directory or jar file containing output dex files. */
    val outputPath: File,

    /** Parameters for dexing. */
    val dexParams: DexParametersForWorkers,

    /** Whether incremental information is available. */
    val isIncremental: Boolean,

    /**
     * The set of all changed (removed, modified, added) files, including those in input files and
     * classpath.
     */
    val changedFiles: Set<File>,

    /**
     * The precomputed set of files that are impacted by the changed files, or `null` if it is
     * intended to be computed later.
     */
    val impactedFiles: Set<File>?,

    /**
     * The file containing the desugaring graph, used to compute the set of impacted files if it was
     * not precomputed. This file is not `null` iff `impactedFiles == null`.
     */
    val desugarGraphFile: File?

) : Serializable {

    init {
        check((impactedFiles == null) xor (desugarGraphFile == null))
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}