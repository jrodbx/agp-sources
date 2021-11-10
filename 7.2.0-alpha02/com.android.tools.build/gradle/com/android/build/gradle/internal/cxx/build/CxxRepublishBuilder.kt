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

import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.cxx.io.synchronizeFile
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons.getNativeBuildMiniConfigs
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.lifecycleln
import com.android.build.gradle.internal.cxx.model.toJsonString
import org.gradle.process.ExecOperations
import java.io.File

/**
 * A CxxBuilder that symlinks (or copies) files from [soFolder] to [soRepublishFolder].
 */
class CxxRepublishBuilder(val model: CxxConfigurationModel) : CxxBuilder {
    // objFolder must be here for legacy reasons but its value was never correct for CMake.
    // There is no folder that has .o files for the entire variant.
    val objFolder: File get() =
        (model.activeAbis + model.unusedAbis).first().intermediatesParentFolder
    val soFolder: File get() =
        (model.activeAbis + model.unusedAbis).first().intermediatesParentFolder
    override fun build(ops: ExecOperations) {
        infoln("link or copy build outputs to republish point")
        val abis = model.activeAbis
        val miniConfigs = getNativeBuildMiniConfigs(abis, null)
        for (config in miniConfigs) {
            for (library in config.libraries.values) {
                val baseOutputLibrary = library.output ?: continue
                if (baseOutputLibrary.extension != "" && baseOutputLibrary.extension != "so") {
                    infoln("Not republishing $baseOutputLibrary because it wasn't an executable type")
                    continue
                }
                val abi = abis.single { it.abi.tag == library.abi }

                if (!baseOutputLibrary.canonicalPath.startsWith(abi.soFolder.canonicalPath)) {
                    infoln("Not republishing $baseOutputLibrary because it wasn't under ${abi.soFolder}")
                    continue
                }
                // Determine the subfolder segment baseOutputLibrary with respect to the
                // ABI's soFolder.
                val subfolderSegment = baseOutputLibrary.relativeTo(abi.soFolder)
                // The file will be republished with the same subfolder segment but now
                // under soRepublishFolder.
                val republishOutputLibrary = abi.soRepublishFolder.resolve(subfolderSegment).canonicalFile

                synchronizeFile(
                    baseOutputLibrary,
                    republishOutputLibrary)

                for (runtimeFile in library.runtimeFiles) {
                    synchronizeFile(
                        runtimeFile,
                        abi.soRepublishFolder.resolve(runtimeFile.name))
                }
            }
        }

        // Symlink STL .so if any
        for(abi in model.activeAbis) {
            if (abi.stlLibraryFile == null) continue
            if (!abi.stlLibraryFile.isFile) continue
            if (!abi.soRepublishFolder.isDirectory) continue
            val objAbi = abi.soRepublishFolder.resolve(abi.stlLibraryFile.name)
            synchronizeFile(
                abi.stlLibraryFile,
                objAbi)
        }
    }
}
