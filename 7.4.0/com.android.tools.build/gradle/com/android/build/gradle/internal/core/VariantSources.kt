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

package com.android.build.gradle.internal.core

import com.android.SdkConstants
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.utils.immutableMapBuilder
import com.android.builder.core.ComponentType
import com.android.builder.model.v2.CustomSourceDirectory
import com.android.builder.model.SourceProvider
import com.google.common.collect.Lists
import java.io.File
import java.util.function.Function

/**
 * Represents the sources for a Variant
 */
class VariantSources internal constructor(
    val fullName: String,
    val componentType: ComponentType,
    private val defaultSourceProvider: SourceProvider,
    private val buildTypeSourceProvider: SourceProvider? = null,
    /** The list of product flavors. Items earlier in the list override later items.  */
    private val flavorSourceProviders: List<SourceProvider>,
    /** MultiFlavors specific source provider, may be null  */
    val multiFlavorSourceProvider: DefaultAndroidSourceSet? = null,
    /** Variant specific source provider, may be null  */
    val variantSourceProvider: DefaultAndroidSourceSet? = null
) {

    /**
     * Returns the path to the main manifest file. It may or may not exist.
     *
     *
     * Note: Avoid calling this method at configuration time because the final path to the
     * manifest file may change during that time.
     */
    val mainManifestFilePath: File
        get() = defaultSourceProvider.manifestFile

    /**
     * Returns the path to the main manifest file if it exists, or `null` otherwise (e.g., the main
     * manifest file is not required to exist for a test variant or a test project).
     *
     *
     * Note: Avoid calling this method at configuration time because (1) the final path to the
     * manifest file may change during that time, and (2) this method performs I/O.
     */
    val mainManifestIfExists: File?
        get() {
            val mainManifest = mainManifestFilePath
            return if (mainManifest.isFile) {
                mainManifest
            } else null
        }

    /** Returns the path to the art profile file. It may or may not exist. */
    val artProfile: File
        get() {
            // this is really brittle, we need to review where those sources will be located and
            // what we offer to make visible in the SourceProvider interface.
            // src/main/baseline-prof.txt will do for now.
            return File(File(mainManifestFilePath.parent), SdkConstants.FN_ART_PROFILE)
        }

    /**
     * Returns a list of sorted SourceProvider in ascending order of importance. This means that
     * items toward the end of the list take precedence over those toward the start of the list.
     *
     * @return a list of source provider
     */
    val  sortedSourceProviders: List<SourceProvider>
        get() {
            val providers: MutableList<SourceProvider> =
                Lists.newArrayListWithExpectedSize(flavorSourceProviders.size + 4)

            // first the default source provider
            providers.add(defaultSourceProvider)
            // the list of flavor must be reversed to use the right overlay order.
            for (n in flavorSourceProviders.indices.reversed()) {
                providers.add(flavorSourceProviders[n])
            }
            // multiflavor specific overrides flavor
            multiFlavorSourceProvider?.let(providers::add)
            // build type overrides flavors
            buildTypeSourceProvider?.let(providers::add)
            // variant specific overrides all
            variantSourceProvider?.let(providers::add)

            return providers
        }

    val manifestOverlays: List<File>
        get() {
            val inputs = mutableListOf<File>()

            val gatherManifest: (SourceProvider) -> Unit = {
                val variantLocation = it.manifestFile
                if (variantLocation.isFile) {
                    inputs.add(variantLocation)
                }
            }

            variantSourceProvider?.let(gatherManifest)
            buildTypeSourceProvider?.let(gatherManifest)
            multiFlavorSourceProvider?.let(gatherManifest)
            flavorSourceProviders.forEach(gatherManifest)

            return inputs
        }

    fun getSourceFiles(f: Function<SourceProvider, Collection<File>>): Set<File> {
        return sortedSourceProviders.flatMap {
            f.apply(it)
        }.toSet()
    }

     /**
     * Returns a map af all customs source directories registered. Key is the source set name as
     * registered by the user. Value is also a map of source set name to list of folders registered
     * for this source set.
     */
    val customSourceList: Map<String, Collection<CustomSourceDirectory>>
        get() {
            return immutableMapBuilder<String, Collection<CustomSourceDirectory>> {
             sortedSourceProviders.forEach {
                 this.put(it.name, it.customDirectories)
             }
            }.toMap()
        }
}
