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

import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import org.gradle.caching.internal.controller.BuildCacheController
import org.gradle.internal.hash.FileHasher
import java.io.File

/**
 * Stubbed out C/C++ build cache implementation.
 */
class CxxBuildCache(
    private val buildCacheController: BuildCacheController,
    private val fileHasher: FileHasher) {
    private val hashFile = { file : File ->
        if (!file.isFile) {
            error("Could not hash '$file' because it didn't exist")
        }
        fileHasher.hash(file).toString()
    }

    /**
     * Enact C/C++ caching around [build] which is the ninja or ndk-build build
     * operation.
     * Cache for a particular [abi].
     * Only load and store targets in [buildTargets]. If [buildTargets] is empty
     * then load and store all .cpp to .o translations.
     */
    fun cacheBuild(abi : CxxAbiModel, buildTargets: Set<String>, build : () -> Unit) {
        if (!buildCacheController.isEnabled) {
            build()
            return
        }
        // TODO(182809209) -- Enable SourceToObjectScope
//        val cacheScope = SourceToObjectCacheScope(
//            listOf(abi),
//            buildTargets,
//            buildCacheController,
//            hashFile
//        )
        // TODO(182809209) -- Enable SourceToObjectScope
        // cacheScope.restoreObjectFiles()
        try {
            build()
        } catch (e : Exception) {
            // TODO(182809209) -- Enable SourceToObjectScope
            // cacheScope.storeObjectFiles(buildFailed = true)
            throw e
        }
        // TODO(182809209) -- Enable SourceToObjectScope
        // cacheScope.storeObjectFiles(buildFailed = false)
    }
}


