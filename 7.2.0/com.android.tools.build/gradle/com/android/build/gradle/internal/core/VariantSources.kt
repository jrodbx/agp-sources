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
import com.android.builder.core.BuilderConstants
import com.android.builder.core.VariantType
import com.android.builder.model.v2.CustomSourceDirectory
import com.android.builder.model.SourceProvider
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.AssetSet
import com.android.ide.common.resources.ResourceSet
import com.google.common.collect.Lists
import java.io.File
import java.util.function.Function

/**
 * Represents the sources for a Variant
 */
class VariantSources internal constructor(
    val fullName: String,
    val variantType: VariantType,
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

    val artProfileIfExists: File?
        get() {
            // this is really brittle, we need to review where those sources will be located and
            // what we offer to make visible in the SourceProvider interface.
            // src/main/baseline-prof.txt will do for now.
            val composeFile = File(
                    File(defaultSourceProvider.manifestFile.parent),
                    SdkConstants.FN_ART_PROFILE)
            return if (composeFile.isFile) {
                composeFile
            } else null
        }

    /**
     * Returns a list of sorted SourceProvider in ascending order of importance. This means that
     * items toward the end of the list take precedence over those toward the start of the list.
     *
     * @return a list of source provider
     */
    val sortedSourceProviders: List<SourceProvider>
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
    fun getResourceSets(validateEnabled: Boolean, aaptEnv: String?): List<ResourceSet> {
        val resourceSets: MutableList<ResourceSet> =
            Lists.newArrayList()
        val mainResDirs =
            defaultSourceProvider.resDirectories
        // the main + generated res folders are in the same ResourceSet
        var resourceSet = ResourceSet(
            BuilderConstants.MAIN, ResourceNamespace.RES_AUTO, null, validateEnabled, aaptEnv
        )
        resourceSet.addSources(mainResDirs)
        resourceSets.add(resourceSet)
        // the list of flavor must be reversed to use the right overlay order.
        for (n in flavorSourceProviders.indices.reversed()) {
            val sourceProvider = flavorSourceProviders[n]
            val flavorResDirs = sourceProvider.resDirectories

            // we need the same of the flavor config, but it's in a different list.
            // This is fine as both list are parallel collections with the same number of items.
            resourceSet = ResourceSet(
                sourceProvider.name,
                ResourceNamespace.RES_AUTO,
                null,
                validateEnabled,
                aaptEnv
            )
            resourceSet.addSources(flavorResDirs)
            resourceSets.add(resourceSet)
        }
        // multiflavor specific overrides flavor
        multiFlavorSourceProvider?.let {
            val variantResDirs = it.resDirectories
            resourceSet = ResourceSet(
                multiFlavorSourceProvider.name,
                ResourceNamespace.RES_AUTO,
                null,
                validateEnabled,
                aaptEnv
            )
            resourceSet.addSources(variantResDirs)
            resourceSets.add(resourceSet)
        }

        // build type overrides the flavors
        buildTypeSourceProvider?.let {
            val typeResDirs = it.resDirectories
            resourceSet = ResourceSet(
                buildTypeSourceProvider.name,
                ResourceNamespace.RES_AUTO,
                null,
                validateEnabled,
                aaptEnv
            )
            resourceSet.addSources(typeResDirs)
            resourceSets.add(resourceSet)
        }

        // variant specific overrides all
        variantSourceProvider?.let {
            val variantResDirs = it.resDirectories
            resourceSet = ResourceSet(
                variantSourceProvider.name,
                ResourceNamespace.RES_AUTO,
                null,
                validateEnabled,
                aaptEnv
            )
            resourceSet.addSources(variantResDirs)
            resourceSets.add(resourceSet)
        }

        return resourceSets
    }

    /**
     * Returns the dynamic list of [AssetSet] based on the configuration, for a particular
     * property of [SourceProvider].
     *
     *
     * The list is ordered in ascending order of importance, meaning the first set is meant to be
     * overridden by the 2nd one and so on. This is meant to facilitate usage of the list in an
     * asset merger
     *
     * @param function the function that return a collection of file based on the SourceProvider.
     * this is usually a method reference on SourceProvider
     * @param aaptEnv the value of "ANDROID_AAPT_IGNORE" environment variable.
     * @return a list ResourceSet.
     */
    fun getSourceFilesAsAssetSets(
        function: Function<SourceProvider, Collection<File>>,
        aaptEnv: String?
    ): List<AssetSet> {
        val assetSets = mutableListOf<AssetSet>()

        val mainResDirs = function.apply(defaultSourceProvider)
        // the main + generated asset folders are in the same AssetSet
        var assetSet = AssetSet(BuilderConstants.MAIN, aaptEnv)
        assetSet.addSources(mainResDirs)
        assetSets.add(assetSet)
        // the list of flavor must be reversed to use the right overlay order.
        for (n in flavorSourceProviders.indices.reversed()) {
            val sourceProvider = flavorSourceProviders[n]
            val flavorResDirs = function.apply(sourceProvider)
            // we need the same of the flavor config, but it's in a different list.
            // This is fine as both list are parallel collections with the same number of items.
            assetSet = AssetSet(sourceProvider.name, aaptEnv)
            assetSet.addSources(flavorResDirs)
            assetSets.add(assetSet)
        }

        // multiflavor specific overrides flavor
        multiFlavorSourceProvider?.let {
            val variantResDirs = function.apply(it)
            assetSet = AssetSet(multiFlavorSourceProvider.name, aaptEnv)
            assetSet.addSources(variantResDirs)
            assetSets.add(assetSet)
        }

        // build type overrides flavors
        if (buildTypeSourceProvider != null) {
            val typeResDirs = function.apply(buildTypeSourceProvider)
            assetSet = AssetSet(buildTypeSourceProvider.name, aaptEnv)
            assetSet.addSources(typeResDirs)
            assetSets.add(assetSet)
        }

        // variant specific overrides all
        variantSourceProvider?.let {
            val variantResDirs = function.apply(it)
            assetSet = AssetSet(variantSourceProvider.name, aaptEnv)
            assetSet.addSources(variantResDirs)
            assetSets.add(assetSet)
        }

        return assetSets
    }

    /**
     * Returns all the renderscript source folder from the main config, the flavors and the build
     * type.
     *
     * @return a list of folders.
     */
    val renderscriptSourceList: Collection<File>
        get() = getSourceFiles(
            Function { obj: SourceProvider -> obj.renderscriptDirectories }
        )

    val aidlSourceList: Collection<File>
        get() = getSourceFiles(
            Function { obj: SourceProvider -> obj.aidlDirectories }
        )

    val jniSourceList: Collection<File>
        get() = getSourceFiles(
            Function { obj: SourceProvider -> obj.cDirectories }
        )

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
