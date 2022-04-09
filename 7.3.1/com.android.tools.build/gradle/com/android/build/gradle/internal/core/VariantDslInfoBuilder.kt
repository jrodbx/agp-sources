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

import com.android.build.api.component.impl.ComponentIdentityImpl
import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.variant.ComponentIdentity
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.core.VariantDslInfoBuilder.Companion.getBuilder
import com.android.build.gradle.internal.dsl.ApplicationPublishingImpl
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.InternalApplicationExtension
import com.android.build.gradle.internal.dsl.InternalLibraryExtension
import com.android.build.gradle.internal.dsl.LibraryPublishingImpl
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.manifest.ManifestDataProvider
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.utils.createPublishingInfoForApp
import com.android.build.gradle.internal.utils.createPublishingInfoForLibrary
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.build.gradle.internal.variant.DimensionCombination
import com.android.builder.core.ComponentType
import com.android.builder.model.SourceProvider
import com.android.utils.appendCapitalized
import com.android.utils.combineAsCamelCase
import org.gradle.api.file.DirectoryProperty

/** Builder for [VariantDslInfo].
 *
 * This allows setting all temporary items on the builder before actually
 * instantiating the configuration, in order to keep it immutable.
 *
 * Use [getBuilder] as an entry point.
 */
class VariantDslInfoBuilder<CommonExtensionT: CommonExtension<*, *, *, *>> private constructor(
    private val dimensionCombination: DimensionCombination,
    val componentType: ComponentType,
    private val defaultConfig: DefaultConfig,
    private val defaultSourceProvider: SourceProvider,
    private val buildType: BuildType,
    private val buildTypeSourceProvider: SourceProvider? = null,
    private val signingConfigOverride: SigningConfig?,
    private val manifestDataProvider: ManifestDataProvider,
    private val dslServices: DslServices,
    private val variantServices: VariantServices,
    private val oldExtension: BaseExtension,
    private val extension: CommonExtensionT,
    private val hasDynamicFeatures: Boolean,
    private val experimentalProperties: Map<String, Any>,
    private val testFixtureMainVariantName: String?
) {

    companion object {
        /**
         * Returns a new builder
         */
        @JvmStatic
        fun <CommonExtensionT: CommonExtension<*, *, *, *>> getBuilder(
            dimensionCombination: DimensionCombination,
            componentType: ComponentType,
            defaultConfig: DefaultConfig,
            defaultSourceSet: SourceProvider,
            buildType: BuildType,
            buildTypeSourceSet: SourceProvider? = null,
            signingConfigOverride: SigningConfig? = null,
            manifestDataProvider: ManifestDataProvider,
            dslServices: DslServices,
            variantServices: VariantServices,
            oldExtension: BaseExtension,
            extension: CommonExtensionT,
            hasDynamicFeatures: Boolean,
            experimentalProperties: Map<String, Any> = mapOf(),
            testFixtureMainVariantName: String? = null
        ): VariantDslInfoBuilder<CommonExtensionT> {
            return VariantDslInfoBuilder(
                dimensionCombination,
                componentType,
                defaultConfig,
                defaultSourceSet,
                buildType,
                buildTypeSourceSet,
                signingConfigOverride,
                manifestDataProvider,
                dslServices,
                variantServices,
                oldExtension,
                extension,
                hasDynamicFeatures,
                experimentalProperties,
                testFixtureMainVariantName,
            )
        }

        /**
         * Returns the full, unique name of the variant in camel case (starting with a lower case),
         * including BuildType, Flavors and Test (if applicable).
         *
         * This is to be used for the normal variant name. In case of Feature plugin, the library
         * side will be called the same as for library plugins, while the feature side will add
         * 'feature' to the name.
         *
         * Also computes the flavor name if applicable
         */
        @JvmStatic
        @JvmOverloads
        fun computeName(
            dimensionCombination: DimensionCombination,
            componentType: ComponentType,
            flavorNameCallback: ((String) -> Unit)? = null
        ): String {
            // compute the flavor name
            val flavorName = if (dimensionCombination.productFlavors.isNullOrEmpty()) {
                ""
            } else {
                combineAsCamelCase(dimensionCombination.productFlavors, Pair<String,String>::second)
            }
            flavorNameCallback?.let { it(flavorName) }

            val sb = StringBuilder()
            val buildType = dimensionCombination.buildType
            if (buildType == null) {
                if (flavorName.isNotEmpty()) {
                    sb.append(flavorName)
                } else if (!componentType.isTestComponent && !componentType.isTestFixturesComponent) {
                    sb.append("main")
                }
            } else {
                if (flavorName.isNotEmpty()) {
                    sb.append(flavorName)
                    sb.appendCapitalized(buildType)
                } else {
                    sb.append(buildType)
                }
            }
            if (componentType.isNestedComponent) {
                if (sb.isEmpty()) {
                    // need the lower case version
                    sb.append(componentType.prefix)
                } else {
                    sb.append(componentType.suffix)
                }
            }
            return sb.toString()
        }

        /**
         * Turns a string into a valid source set name for the given [ComponentType], e.g.
         * "fooBarUnitTest" becomes "testFooBar".
         */
        @JvmStatic
        fun computeSourceSetName(
            baseName: String,
            componentType: ComponentType
        ): String {
            var name = baseName
            if (name.endsWith(componentType.suffix)) {
                name = name.substring(0, name.length - componentType.suffix.length)
            }
            if (!componentType.prefix.isEmpty()) {
                name = componentType.prefix.appendCapitalized(name)
            }
            return name
        }

        /**
         * Returns the full, unique name of the variant, including BuildType, flavors and test, dash
         * separated. (similar to full name but with dashes)
         *
         * @return the name of the variant
         */
        @JvmStatic
        fun computeBaseName(
            dimensionCombination: DimensionCombination,
            componentType: ComponentType) : String {
            val sb = StringBuilder()
            if (dimensionCombination.productFlavors.isNotEmpty()) {
                for ((_, name) in dimensionCombination.productFlavors) {
                    if (sb.isNotEmpty()) {
                        sb.append('-')
                    }
                    sb.append(name)
                }
            }

            dimensionCombination.buildType?.let {
                if (sb.isNotEmpty()) {
                    sb.append('-')
                }
                sb.append(it)
            }

            if (componentType.isNestedComponent) {
                if (sb.isNotEmpty()) {
                    sb.append('-')
                }
                sb.append(componentType.prefix)
            }

            if (sb.isEmpty()) {
                sb.append("main")
            }

            return sb.toString()
        }

        /**
         * Returns a full name that includes the given splits name.
         *
         * @param splitName the split name
         * @return a unique name made up of the variant and split names.
         */
        @JvmStatic
        fun computeFullNameWithSplits(
            variantConfiguration: ComponentIdentity,
            componentType: ComponentType,
            splitName: String): String {
            val sb = StringBuilder()

            val flavorName = variantConfiguration.flavorName

            if (!flavorName.isNullOrEmpty()) {
                sb.append(flavorName)
                sb.appendCapitalized(splitName)
            } else {
                sb.append(splitName)
            }

            variantConfiguration.buildType?.let {
                sb.appendCapitalized(it)
            }

            if (componentType.isNestedComponent) {
                sb.append(componentType.suffix)
            }
            return sb.toString()
        }
    }

    private lateinit var variantName: String
    private lateinit var multiFlavorName: String

    val name: String
        get() {
            if (!::variantName.isInitialized) {
                computeNames()
            }

            return variantName
        }

    val flavorName: String
        get() {
            if (!::multiFlavorName.isInitialized) {
                computeNames()
            }
            return multiFlavorName

        }

    private val flavors = mutableListOf<Pair<ProductFlavor, SourceProvider>>()

    var variantSourceProvider: DefaultAndroidSourceSet? = null
    var multiFlavorSourceProvider: DefaultAndroidSourceSet? = null
    var productionVariant: VariantDslInfoImpl? = null
    var inconsistentTestAppId: Boolean = false

    fun addProductFlavor(
        productFlavor: ProductFlavor,
        sourceProvider: SourceProvider
    ) {
        if (::variantName.isInitialized) {
            throw RuntimeException("call to getName() before calling all addProductFlavor")
        }
        flavors.add(Pair(productFlavor, sourceProvider))
    }

    /** Creates a variant configuration  */
    fun createVariantDslInfo(buildDirectory: DirectoryProperty): VariantDslInfoImpl {
        val flavorList = flavors.map { it.first }

        val publishingInfo = if (extension is InternalLibraryExtension) {
            createPublishingInfoForLibrary(
                extension.publishing as LibraryPublishingImpl,
                dslServices.projectOptions,
                name,
                buildType,
                flavorList,
                extension.buildTypes,
                extension.productFlavors,
                testFixtureMainVariantName,
                dslServices.issueReporter
            )
        } else if (extension is InternalApplicationExtension) {
            createPublishingInfoForApp(
                extension.publishing as ApplicationPublishingImpl,
                dslServices.projectOptions,
                name,
                hasDynamicFeatures,
                dslServices.issueReporter
            )
        } else null

        return VariantDslInfoImpl(
            ComponentIdentityImpl(
                name,
                flavorName,
                dimensionCombination.buildType,
                dimensionCombination.productFlavors
            ),
            componentType,
            defaultConfig,
            buildType,
            // this could be removed once the product flavor is internal only.
            flavorList.toImmutableList(),
            signingConfigOverride,
            productionVariant,
            manifestDataProvider,
            dslServices,
            variantServices,
            buildDirectory,
            publishingInfo,
            experimentalProperties,
            inconsistentTestAppId,
            oldExtension,
            extension
        )
    }

    fun createVariantSources(): VariantSources {
        return VariantSources(
            name,
            componentType,
            defaultSourceProvider,
            buildTypeSourceProvider,
            flavors.map { it.second }.toImmutableList(),
            multiFlavorSourceProvider,
            variantSourceProvider
        )
    }

    /**
     * computes the name for the variant and the multi-flavor combination
     */
    private fun computeNames() {
        variantName = computeName(dimensionCombination, componentType) {
            multiFlavorName = it
        }
    }
}
