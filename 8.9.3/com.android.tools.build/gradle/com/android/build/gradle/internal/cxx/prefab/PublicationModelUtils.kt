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

package com.android.build.gradle.internal.cxx.prefab

import com.android.build.gradle.internal.cxx.json.readMiniConfigCreateIfNecessary

/**
 * Strip the ABIs from the given publication leaving the publication as header-only.
 */
fun PrefabPublication.copyAsHeaderOnly() : PrefabPublication {
    return copy(
        packageInfo = packageInfo.copy(
            modules = packageInfo.modules.map { module ->
                module.copy(
                    abis = listOf()
                )
            }
        )
    )
}

/**
 * Strip all but one ABI from the publication.
 */
fun PrefabPublication.copyAsSingleAbi(abiName : String) : PrefabPublication {
    return copy(
        packageInfo = packageInfo.copy(
            modules = packageInfo.modules.map { module ->
                module.copy(
                    abis = module.abis.filter { abi -> abi.abiName == abiName }
                )
            }
        )
    )
}

/**
 * Copy this [PrefabPublication] and add then names of the libraries (.so or .a) for each ABI
 * and module combination.
 */
fun PrefabPublication.copyWithLibraryInformationAdded() : PrefabPublication {
    fun PrefabModulePublication.patchLibraryType() : PrefabModulePublication {
        if (abis.isEmpty()) return this
        val abiLibraries = abis
            .mapNotNull { abi ->
                readMiniConfigCreateIfNecessary(abi.abiAndroidGradleBuildJsonFile)
                    .libraries
                    .values
                    .singleOrNull { it.artifactName == moduleName }
                    ?.output
                    ?.let { abi to it }
            }

        return copy(
            abis = abiLibraries.map { (abi, library) ->
                abi.copy(abiLibrary = library.absoluteFile)
            })
    }

    return copy(
        packageInfo = packageInfo.copy(
            modules = packageInfo.modules.map { it.patchLibraryType() }
        )
    )
}

