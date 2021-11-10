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

package com.android.build.gradle.internal.manifest

import com.android.build.gradle.internal.services.ProjectServices
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import java.util.function.BooleanSupplier

/**
 * a lazy manifest parser that can create a `Provider<ManifestData>`
 */
class LazyManifestParser(
    private val manifestFile: Provider<RegularFile>,
    private val manifestFileRequired: Boolean,
    private val projectServices: ProjectServices,
    private val manifestParsingAllowed: BooleanSupplier
): ManifestDataProvider {

     override val manifestData: Provider<ManifestData> by lazy {
        // using map will allow us to keep task dependency should the manifest be generated or
        // transformed via a task
        val provider = manifestFile.map {
            parseManifest(
                it.asFile,
                manifestFileRequired,
                manifestParsingAllowed,
                projectServices.issueReporter
            )
        }

        // wrap the provider in a property to allow memoization
        projectServices.objectFactory.property(ManifestData::class.java).also {
            it.set(provider)
            it.finalizeValueOnRead()
            // TODO disable early get
        }
    }

    override val manifestLocation: String
        get() = manifestFile.get().asFile.absolutePath
}
