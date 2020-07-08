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
package com.android.build.gradle.internal.test

import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.BuiltArtifacts
import com.android.build.api.variant.FilterConfiguration
import com.android.builder.testing.api.DeviceConfigProvider
import com.android.resources.Density
import com.google.common.base.Strings
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import java.io.File
import java.util.ArrayList
import java.util.Collections

object SplitOutputMatcher {

    /**
     * Determines and return the list of APKs to use based on given device density and abis.
     *
     * @param deviceConfigProvider the device configuration.
     * @param builtArtifacts the tested variant built artifacts.
     * @param variantAbiFilters a list of abi filters applied to the variant. This is used in place
     * of the outputs, if there is a single output with no abi filters. If the list is null or
     * empty, then the variant does not restrict ABI packaging.
     * @return the list of APK files to install.
     */
    fun computeBestOutput(
        deviceConfigProvider: DeviceConfigProvider,
        builtArtifacts: BuiltArtifacts,
        variantAbiFilters: Collection<String?>?
    ): List<File> {
        val apkFiles: MutableList<File> =
            ArrayList()
        // now look for a matching output file
        return computeBestOutput(
                builtArtifacts,
                variantAbiFilters,
                deviceConfigProvider.density,
                deviceConfigProvider.abis
            )
    }

    /**
     * Determines and return the list of APKs to use based on given device density and abis.
     *
     *
     * This uses the same logic as the store, using two passes: First, find all the compatible
     * outputs. Then take the one with the highest versionCode.
     *
     * @param outputs the outputs to choose from.
     * @param variantAbiFilters a list of abi filters applied to the variant. This is used in place
     * of the outputs, if there is a single output with no abi filters. If the list is null,
     * then the variant does not restrict ABI packaging.
     * @param deviceDensity the density of the device.
     * @param deviceAbis a list of ABIs supported by the device.
     * @return the list of APKs to install or null if none are compatible.
     */
    fun computeBestOutput(
        outputs: BuiltArtifacts,
        variantAbiFilters: Collection<String?>?,
        deviceDensity: Int,
        deviceAbis: List<String?>
    ): List<File> {
        val densityEnum = Density.getEnum(deviceDensity)
        val densityValue: String?
        densityValue = densityEnum?.resourceValue
        // gather all compatible matches.
        val matches: MutableList<BuiltArtifact> =
            Lists.newArrayList()
        // find a matching output.
        for (builtArtifact in outputs.elements) {
            val densityFilter =
                getFilter(builtArtifact, FilterConfiguration.FilterType.DENSITY)
            val abiFilter =
                getFilter(builtArtifact, FilterConfiguration.FilterType.ABI)
            if (densityFilter != null && densityFilter != densityValue) {
                continue
            }
            if (abiFilter != null && !deviceAbis.contains(abiFilter)) {
                continue
            }
            matches.add(builtArtifact)
        }
        if (matches.isEmpty()) {
            return ImmutableList.of()
        }
        val match = Collections.max(
            matches
        ) { splitOutput: BuiltArtifact, splitOutput2: BuiltArtifact ->
            val rc = splitOutput.versionCode - splitOutput2.versionCode
            if (rc != 0) {
                return@max rc
            }
            val abiOrder1 = getAbiPreferenceOrder(splitOutput, deviceAbis)
            val abiOrder2 = getAbiPreferenceOrder(splitOutput2, deviceAbis)
            abiOrder1 - abiOrder2
        }
        return if (isMainApkCompatibleWithDevice(
                match,
                variantAbiFilters,
                deviceAbis
            )
        ) ImmutableList.of(match.outputFile.toFile()) else ImmutableList.of()
    }

    /**
     * Return the preference score of a VariantOutput for the deviceAbi list.
     *
     * Higher score means a better match.  Scores returned by different call are only comparable if
     * the specified deviceAbi is the same.
     */
    private fun getAbiPreferenceOrder(
        builtArtifact: BuiltArtifact,
        deviceAbi: List<String?>
    ): Int {
        val abiFilter = getFilter(builtArtifact, FilterConfiguration.FilterType.ABI)
        if (Strings.isNullOrEmpty(abiFilter)) { // Null or empty imply a universal APK, which would return the second highest score.
            return deviceAbi.size - 1
        }
        var match = deviceAbi.indexOf(abiFilter)
        if (match == 0) {
            // We want to select the output that matches the first deviceAbi.  The filtered output
            // is preferred over universal APK if it matches the first deviceAbi as they are likely
            // to take a shorter time to build.
            match = deviceAbi.size // highest possible score for the specified deviceAbi.
        } else if (match > 0) {
            // Universal APK may contain the best match even though it is not guaranteed, that's
            // why it is preferred over a filtered output that does not match the best ABI.
            match = deviceAbi.size - match - 1
        }
        return match
    }

    private fun isMainApkCompatibleWithDevice(
        mainBuiltArtifact: BuiltArtifact,
        variantAbiFilters: Collection<String?>?,
        deviceAbis: Collection<String?>
    ): Boolean { // so far, we are not dealing with the pure split files...
        if (getFilter(mainBuiltArtifact, FilterConfiguration.FilterType.ABI)
            == null && variantAbiFilters != null && !variantAbiFilters.isEmpty()
        ) { // if we have a match that has no abi filter, and we have variant-level filters, then
// we need to make sure that the variant filters are compatible with the device abis.
            for (abi in deviceAbis) {
                if (variantAbiFilters.contains(abi)) {
                    return true
                }
            }
            return false
        }
        return true
    }

    private fun getFilter(
        variantOutput: BuiltArtifact, filterType: FilterConfiguration.FilterType
    ): String? =
        variantOutput.filters.firstOrNull { it.filterType == filterType }?.identifier
}