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

package com.android.build.gradle.internal.cxx.build

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons.getNativeBuildMiniConfigs
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.utils.FileUtils.join
import org.gradle.caching.internal.controller.BuildCacheController
import org.gradle.process.ExecOperations
import java.io.File
import org.gradle.internal.hash.FileHasher

/**
 * A CxxBuilder that symlinks (or copies) files from [soFolder] to [soRepublishFolder].
 */
class CxxRepublishBuilder(val model: CxxConfigurationModel) : CxxBuilder {
    // objFolder must be here for legacy reasons but its value was never correct for CMake.
    // There is no folder that has .o files for the entire variant.
    override val objFolder: File get() = model.variant.soFolder
    override val soFolder: File get() = model.variant.soFolder
    override fun build(
        ops: ExecOperations,
        fileHasher: FileHasher,
        buildCacheController: BuildCacheController) {
        infoln("link or copy build outputs to republish point")
        val variant = model.variant
        val abis = model.activeAbis
        val miniConfigs = getNativeBuildMiniConfigs(abis, null)
        for (config in miniConfigs) {
            for (library in config.libraries.values) {
                val output = library.output ?: continue
                if (!output.exists()) continue
                val libraryAbi = library.abi ?: continue
                val abi = Abi.getByName(libraryAbi) ?: continue
                val baseOutputFolder = join(variant.soFolder, abi.tag)
                if (!baseOutputFolder.isDirectory) continue
                val baseRepublishFolder = join(variant.soRepublishFolder, abi.tag)
                baseRepublishFolder.mkdirs()

                hardLinkOrCopy(
                        join(baseOutputFolder, output.name),
                        join(baseRepublishFolder, output.name))

                for (runtimeFile in library.runtimeFiles) {
                    hardLinkOrCopy(
                            join(baseOutputFolder, runtimeFile.name),
                            join(baseRepublishFolder, runtimeFile.name))
                }
            }
        }

        // Symlink STL .so if any
        for(abi in model.activeAbis) {
            if (abi.stlLibraryFile == null) continue
            if (!abi.stlLibraryFile.isFile) continue
            if (!abi.soRepublishFolder.isDirectory) continue
            val objAbi = abi.soRepublishFolder.resolve(abi.stlLibraryFile.name)
            hardLinkOrCopy(abi.stlLibraryFile, objAbi)
        }
    }
}
