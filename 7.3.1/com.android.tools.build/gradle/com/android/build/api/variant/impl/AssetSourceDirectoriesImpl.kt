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

package com.android.build.api.variant.impl

import com.android.SdkConstants
import com.android.build.gradle.internal.services.VariantServices
import com.android.builder.core.BuilderConstants
import com.android.ide.common.resources.AssetSet
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.util.PatternFilterable

/**
 * Specialization of [LayeredSourceDirectoriesImpl] for [SourceType.ASSETS]
 */
class AssetSourceDirectoriesImpl(
    _name: String,
    val variantServices: VariantServices,
    variantDslFilters: PatternFilterable?,
) : LayeredSourceDirectoriesImpl(_name, variantServices, variantDslFilters) {

    /**
     * Returns the dynamic list of [AssetSet] based on the current list of [DirectoryEntry]
     *
     * The list is ordered in ascending order of importance, meaning the first set is meant to be
     * overridden by the 2nd one and so on. This is meant to facilitate usage of the list in an
     * asset merger
     *
     * @param aaptEnv the value of "ANDROID_AAPT_IGNORE" environment variable.
     * @return a [Provider] of a [List] of [AssetSet].
     */
    fun getAscendingOrderAssetSets(
        aaptEnv: Provider<String>
    ): Provider<List<AssetSet>> {

        return super.variantSources.map { allDirectories ->
            allDirectories.map { directoryEntries ->
                val assetName = if (directoryEntries.name == SdkConstants.FD_MAIN)
                    BuilderConstants.MAIN else directoryEntries.name

                AssetSet(assetName, aaptEnv.orNull).also {
                    it.addSources(directoryEntries.directoryEntries.map { directoryEntry ->
                        directoryEntry.asFiles(variantServices::directoryProperty).get().asFile
                    })
                }
            }
        }
    }
}
