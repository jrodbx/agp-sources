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

package com.android.ide.common.resources

import com.android.ide.common.blame.SourceFilePosition
import com.android.ide.common.blame.SourcePosition
import java.io.File
import java.io.Serializable

/** A request for Aapt2 compile / ResourceCompiler.  */
class CompileResourceRequest @JvmOverloads constructor(
    val inputFile: File,
    val outputDirectory: File,
    val inputDirectoryName: String = inputFile.parentFile.name,
    /**
     * Whether the resource comes from a dependency or from the current subproject, or `null` if
     * this information is not available.
     */
    val inputFileIsFromDependency: Boolean? = null,
    val isPseudoLocalize: Boolean = false,
    val isPngCrunching: Boolean = true,
    /** The map of where values came from, so errors are reported correctly. */
    val blameMap: Map<SourcePosition, SourceFilePosition> = mapOf(),
    /** The original source file. For data binding, so errors are reported correctly */
    val originalInputFile: File = inputFile,
    val partialRFile: File? = null,
    /**
     * The folder containing blame logs of where values came from, so errors are reported correctly
     * This should be used in case the folder contents aren't already loaded in memory, otherwise
     * use [blameMap]
     */
    val mergeBlameFolder: File? = null,
    /** Map of source set identifier to absolute path Used for determining relative sourcePath.
     *
     * Not a property due to serialization of sealed interface.
     */
    resourcePathEncoding: ResourcePathEncoding
) : Serializable {

    /* If true, resourcePathEncoding property is an instance of ResourcePathEncoding.Relative */
    val usesRelativePaths: Boolean = resourcePathEncoding is ResourcePathEncoding.Relative

    /* Provides the source set map used for resource encoding the sourceSetProperty */
    val resEncodingSourceSetMap: Map<String, String>? by lazy {
        when (resourcePathEncoding) {
            is ResourcePathEncoding.Relative -> resourcePathEncoding.identifiedSourceSetMap
            is ResourcePathEncoding.AbsoluteNotRelocatable -> null
        }
    }

    val sourcePath : String by lazy {
        when (resourcePathEncoding) {
            is ResourcePathEncoding.Relative -> {
                if (resourcePathEncoding.identifiedSourceSetMap.isEmpty()) {
                    error("No resource source set identifiers found. " +
                                  "Unable to encode relative path in compiled resource ${inputFile.absolutePath}")
                }
                getRelativeSourceSetPath(inputFile, resourcePathEncoding.identifiedSourceSetMap)
            }
            is ResourcePathEncoding.AbsoluteNotRelocatable -> inputFile.absolutePath
        }
    }
}

sealed interface ResourcePathEncoding {
    /* Provides a map of resource source sets to encode a safe relative resource path into the
     * compiled resource that is independent of path changes. This allows for relatability.
    */
    data class Relative(val identifiedSourceSetMap: Map<String, String>) : ResourcePathEncoding

    /* Writes absolute path to the compiled resource. This is problematic for relocatability and caching.
     * Only use for testing.
    */
    object AbsoluteNotRelocatable : ResourcePathEncoding
}
