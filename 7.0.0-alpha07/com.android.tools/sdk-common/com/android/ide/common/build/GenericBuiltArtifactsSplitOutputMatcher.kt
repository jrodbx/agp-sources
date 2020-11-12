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
package com.android.ide.common.build

import com.android.builder.testing.api.DeviceConfigProvider
import com.google.common.base.Strings
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import java.io.File
import java.util.Collections

object GenericBuiltArtifactsSplitOutputMatcher {

    /**
     * Determines and return the list of APKs to use based on given device abis.
     *
     * @param deviceConfigProvider the device configuration.
     * @param builtArtifacts the tested variant built artifacts.
     * @param variantAbiFilters a list of abi filters applied to the variant. This is used in place
     * of the outputs, if there is a single output with no abi filters. If the list is null or
     * empty, then the variant does not restrict ABI packaging.
     * @return the list of APK files to install.
     */
    fun computeBestOutputs(
        deviceConfigProvider: DeviceConfigProvider,
        builtArtifacts: GenericBuiltArtifacts,
        variantAbiFilters: Collection<String>
    ): List<File> {
        // now look for a matching output file
        return computeBestOutput(
            builtArtifacts,
            variantAbiFilters,
            deviceConfigProvider.abis
        )
    }

    /**
     * Determines and return the list of APKs to use based on given device abis.
     *
     *
     * This uses the same logic as the store, using two passes: First, find all the compatible
     * outputs. Then take the one with the highest versionCode.
     *
     * @param outputs the outputs to choose from.
     * @param variantAbiFilters a list of abi filters applied to the variant. This is used in place
     * of the outputs, if there is a single output with no abi filters. If the list is empty
     * then the variant does not restrict ABI packaging.
     * @param deviceAbis a list of ABIs supported by the device.
     * @return the list of APKs to install or null if none are compatible.
     */
    fun computeBestOutput(
        outputs: GenericBuiltArtifacts,
        variantAbiFilters: Collection<String>,
        deviceAbis: List<String?>
    ): List<File> =
        computeBestArtifact(outputs.elements, variantAbiFilters, deviceAbis)?.let {
            ImmutableList.of(File(it.outputFile))
        } ?: ImmutableList.of<File>()


    /**
     * Determines and return the list of APKs to use based on given device abis.
     *
     *
     * This uses the same logic as the store, using two passes: First, find all the compatible
     * outputs. Then take the one with the highest versionCode.
     *
     * @param outputs the outputs to choose from.
     * @param variantAbiFilters a list of abi filters applied to the variant. This is used in place
     * of the outputs, if there is a single output with no abi filters. If the list is empty
     * then the variant does not restrict ABI packaging.
     * @param deviceAbis a list of ABIs supported by the device.
     * @return the list of APKs to install or null if none are compatible.
     */
    fun computeBestArtifact(
        outputs: Collection<GenericBuiltArtifact>,
        variantAbiFilters: Collection<String>,
        deviceAbis: List<String?>
    ): GenericBuiltArtifact? {
        // gather all compatible matches.
        val matches: MutableList<GenericBuiltArtifact> =
            Lists.newArrayList()
        // find a matching output.
        for (builtArtifact in outputs) {
            val abiFilter =
                getFilter(builtArtifact, "ABI")
            if (abiFilter != null && !deviceAbis.contains(abiFilter)) {
                continue
            }
            matches.add(builtArtifact)
        }
        if (matches.isEmpty()) {
            return null
        }
        val match = Collections.max(
            matches
        ) { splitOutput: GenericBuiltArtifact, splitOutput2: GenericBuiltArtifact ->
            val rc = versionCodeDiff(splitOutput.versionCode, splitOutput2.versionCode)
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
        ) match else null
    }

    /**
     * Computes a - b.
     *
     * null values are considered to be the same as 1 since this is how the platform handles missing
     * version code from the manifest
     */
    private fun versionCodeDiff(a: Int?, b: Int?): Int = (a ?: 1) - (b ?: 1)

    /**
     * Return the preference score of a VariantOutput for the deviceAbi list.
     *
     * Higher score means a better match.  Scores returned by different call are only comparable if
     * the specified deviceAbi is the same.
     */
    private fun getAbiPreferenceOrder(
        builtArtifact: GenericBuiltArtifact,
        deviceAbi: List<String?>
    ): Int {
        val abiFilter = getFilter(builtArtifact, "ABI")
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
        mainBuiltArtifact: GenericBuiltArtifact,
        variantAbiFilters: Collection<String>,
        deviceAbis: Collection<String?>
    ): Boolean { // so far, we are not dealing with the pure split files...
        if ((getFilter(mainBuiltArtifact, "ABI") == null) && !variantAbiFilters.isEmpty()
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
        variantOutput: GenericBuiltArtifact, filterType: String
    ): String? =
        variantOutput.filters.firstOrNull { it.filterType == filterType }?.identifier
}