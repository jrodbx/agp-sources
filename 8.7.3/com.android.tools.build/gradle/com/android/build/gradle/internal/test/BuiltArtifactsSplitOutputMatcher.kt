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

import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.builder.testing.api.DeviceConfigProvider
import com.android.ide.common.build.GenericBuiltArtifactsSplitOutputMatcher
import java.io.File

object BuiltArtifactsSplitOutputMatcher {

    /**
     * Determines and return the list of APKs to use based on given device abis.
     *
     * @param deviceConfigProvider the device configuration.
     * @param builtArtifacts the tested variant built artifacts.
     * @param variantAbiFilters a list of abi filters applied to the variant. This is used in place
     * of the outputs, if there is a single output with no abi filters. If the list is
     * empty, then the variant does not restrict ABI packaging.
     * @return the list of APK files to install.
     */
    fun computeBestOutput(
        deviceConfigProvider: DeviceConfigProvider,
        builtArtifacts: BuiltArtifactsImpl,
        variantAbiFilters: Collection<String>
    ): List<File> {
        val adaptedBuiltArtifactType = builtArtifacts.toGenericBuiltArtifacts()
        // now look for a matching output file
        return GenericBuiltArtifactsSplitOutputMatcher.computeBestOutput(
            adaptedBuiltArtifactType,
            variantAbiFilters,
            deviceConfigProvider.abis
        )
    }
}
