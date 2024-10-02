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

package com.android.build.api.variant.impl

import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.VariantOutput
import com.android.build.api.variant.VariantOutputConfiguration
import org.gradle.api.tasks.Nested

/**
 * Implementation of [List] of [VariantOutput] with added private services for AGP.
 */
class VariantOutputList(
    @get:Nested
    val variantOutputs: List<VariantOutputImpl>,
        private val targetConfigurations: Collection<FilterConfiguration>? = null): List<VariantOutputImpl> by variantOutputs {

    /**
     * Returns the list of enabled [VariantOutput]
     */
    fun getEnabledVariantOutputs(): List<VariantOutputImpl> =
            variantOutputs.filter { it.enabled.get() }

    /**
     * Finds the main split in the current variant context or throws a [RuntimeException] if there
     * are none.
     */
    fun getMainSplit(): VariantOutputImpl =
            getMainSplitOrNull()
                    ?: throw RuntimeException("Cannot determine main split information, file a bug.")

    /**
     * Finds the main split in the current variant context or null if there are no variant output.
     */
    fun getMainSplitOrNull(): VariantOutputImpl? =
            variantOutputs.find { variantOutput ->
                variantOutput.outputType == VariantOutputConfiguration.OutputType.SINGLE
            }
                    ?: variantOutputs.find {
                        it.outputType == VariantOutputConfiguration.OutputType.UNIVERSAL
                    }
                    ?: targetConfigurations?.let {
                        variantOutputs
                                .asSequence()
                                .filter { it.outputType == VariantOutputConfiguration.OutputType.ONE_OF_MANY }
                            .maxWithOrNull { output1, output2 ->
                                findBetterMatch(output1, output2, targetConfigurations)
                            }
                    }
                    ?: variantOutputs.find {
                        it.outputType == VariantOutputConfiguration.OutputType.ONE_OF_MANY
                    }


    companion object {
        /**
         * Compares two variant outputs for how closely they match with the given targetConfigurations.
         *
         * When there are multiple variant outputs, we choose the variant output with filters that match
         * the closest with the selected device. For example when the abi splits are enabled, we generate
         * multiple variant output, one for each abi. In this case, we choose the variant output with abi
         * that matches with the supported abi of the selected device in IDE as the main split.
         */
        fun findBetterMatch(
            variantOutputConfiguration1: VariantOutputConfiguration,
            variantOutputConfiguration2: VariantOutputConfiguration,
            targetConfigurations: Collection<FilterConfiguration>): Int {
            val outputFilterMap1 = variantOutputConfiguration1.filters.associate { it.filterType to it.identifier }
            val outputFilterMap2 = variantOutputConfiguration2.filters.associate { it.filterType to it.identifier }
            for(filterConfiguration in targetConfigurations) {
                // when device supports more than one value of a filter type, the filter
                // identifier is a string formed by appending all the values separated by comma.
                // For ex. for device that support two abis: x86, armv7a, the filter identifier will be
                // "x86,armv7a". If we find two matching variant outputs, we would choose the one that
                // is more preferred by looking at the matching index in target filter identifier string.
                val filterIndex1 = outputFilterMap1[filterConfiguration.filterType]
                    ?.let { filterConfiguration.identifier.indexOf(it) } ?: -1
                val filterIndex2 = outputFilterMap2[filterConfiguration.filterType]
                    ?.let { filterConfiguration.identifier.indexOf(it) } ?: -1
                val result = compareValues(filterIndex1, filterIndex2)
                if (result != 0) {
                    return if (filterIndex1 >= 0 && filterIndex2 >= 0) (-1 * result) else result
                }
            }
            return 0
        }
    }
}
