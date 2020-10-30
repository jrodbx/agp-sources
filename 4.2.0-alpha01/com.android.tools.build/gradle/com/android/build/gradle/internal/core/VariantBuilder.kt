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

import com.android.build.api.component.ComponentIdentity
import com.android.build.api.component.impl.ComponentIdentityImpl
import com.android.build.gradle.internal.core.VariantBuilder.Companion.getBuilder
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.manifest.ManifestDataProvider
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.build.gradle.internal.variant.DimensionCombination
import com.android.builder.core.VariantType
import com.android.builder.model.SourceProvider
import com.android.utils.appendCapitalized
import com.android.utils.combineAsCamelCase

/** Builder for [VariantDslInfo].
 *
 * This allows setting all temporary items on the builder before actually
 * instantiating the configuration, in order to keep it immutable.
 *
 * Use [getBuilder] as an entry point.
 */
class VariantBuilder private constructor(
    private val dimensionCombination: DimensionCombination,
    val variantType: VariantType,
    private val defaultConfig: DefaultConfig,
    private val defaultSourceProvider: SourceProvider,
    private val buildType: BuildType,
    private val buildTypeSourceProvider: SourceProvider? = null,
    private val signingConfigOverride: SigningConfig?,
    private val manifestDataProvider: ManifestDataProvider,
    private val dslServices: DslServices,
    private val variantPropertiesApiServices: VariantPropertiesApiServices
) {

    companion object {
        /**
         * Returns a new builder
         */
        @JvmStatic
        fun getBuilder(
            dimensionCombination: DimensionCombination,
            variantType: VariantType,
            defaultConfig: DefaultConfig,
            defaultSourceSet: SourceProvider,
            buildType: BuildType,
            buildTypeSourceSet: SourceProvider? = null,
            signingConfigOverride: SigningConfig? = null,
            manifestDataProvider: ManifestDataProvider,
            dslServices: DslServices,
            variantPropertiesApiServices: VariantPropertiesApiServices
        ): VariantBuilder {
            return VariantBuilder(
                dimensionCombination,
                variantType,
                defaultConfig,
                defaultSourceSet,
                buildType,
                buildTypeSourceSet,
                signingConfigOverride,
                manifestDataProvider,
                dslServices,
                variantPropertiesApiServices
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
            variantType: VariantType,
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
                } else if (!variantType.isTestComponent) {
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
            if (variantType.isTestComponent) {
                if (sb.isEmpty()) {
                    // need the lower case version
                    sb.append(variantType.prefix)
                } else {
                    sb.append(variantType.suffix)
                }
            }
            return sb.toString()
        }

        /**
         * Turns a string into a valid source set name for the given [VariantType], e.g.
         * "fooBarUnitTest" becomes "testFooBar".
         */
        @JvmStatic
        fun computeSourceSetName(
            baseName: String,
            variantType: VariantType
        ): String {
            var name = baseName
            if (name.endsWith(variantType.suffix)) {
                name = name.substring(0, name.length - variantType.suffix.length)
            }
            if (!variantType.prefix.isEmpty()) {
                name = variantType.prefix.appendCapitalized(name)
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
            variantType: VariantType) : String {
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

            if (variantType.isTestComponent) {
                if (sb.isNotEmpty()) {
                    sb.append('-')
                }
                sb.append(variantType.prefix)
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
            variantType: VariantType,
            splitName: String): String {
            val sb = StringBuilder()

            val flavorName = variantConfiguration.flavorName

            if (flavorName.isNotEmpty()) {
                sb.append(flavorName)
                sb.appendCapitalized(splitName)
            } else {
                sb.append(splitName)
            }

            variantConfiguration.buildType?.let {
                sb.appendCapitalized(it)
            }

            if (variantType.isTestComponent) {
                sb.append(variantType.suffix)
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

    var variantSourceProvider: SourceProvider? = null
    var multiFlavorSourceProvider: SourceProvider? = null
    var testedVariant: VariantDslInfoImpl? = null

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
    fun createVariantDslInfo(): VariantDslInfoImpl {
        val flavorList = flavors.map { it.first }

        return VariantDslInfoImpl(
            ComponentIdentityImpl(
                name,
                flavorName,
                dimensionCombination.buildType,
                dimensionCombination.productFlavors
            ),
            variantType,
            defaultConfig,
            buildType,
            // this could be removed once the product flavor is internal only.
            flavorList.toImmutableList(),
            signingConfigOverride,
            testedVariant,
            manifestDataProvider,
            dslServices,
            variantPropertiesApiServices
        )
    }


    fun createVariantSources(): VariantSources {
        return VariantSources(
            name,
            variantType,
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
        variantName = computeName(dimensionCombination, variantType) {
            multiFlavorName = it
        }
    }
}
