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
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceSet
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.util.PatternFilterable

class ResSourceDirectoriesImpl(
    _name: String,
    val variantServices: VariantServices,
    variantDslFilters: PatternFilterable?,
) : LayeredSourceDirectoriesImpl(_name, variantServices, variantDslFilters) {


    /**
     * Returns the dynamic list of [ResourceSet] for the source folders only.
     *
     *
     * The list is ordered in ascending order of importance, meaning the first set is meant to be
     * overridden by the 2nd one and so on. This is meant to facilitate usage of the list in a
     * Resource merger
     *
     * @param aaptEnv the value of "ANDROID_AAPT_IGNORE" environment variable.
     * @return a list ResourceSet.
     */
    fun getAscendingOrderResourceSets(
        validateEnabled: Boolean,
        aaptEnv: String?
    ): Provider<List<ResourceSet>> {

        return super.variantSources.map { allDirectories ->
            allDirectories.map { directoryEntries ->
                val assetName = if (directoryEntries.name == SdkConstants.FD_MAIN)
                    BuilderConstants.MAIN else directoryEntries.name

                ResourceSet(
                    assetName,
                    ResourceNamespace.RES_AUTO,
                    null,
                    validateEnabled,
                    aaptEnv,
                ).also {
                    it.addSources(directoryEntries.directoryEntries.map { directoryEntry ->
                        directoryEntry.asFiles(variantServices::directoryProperty).get().asFile
                    })
                }
            }
        }
    }
}
