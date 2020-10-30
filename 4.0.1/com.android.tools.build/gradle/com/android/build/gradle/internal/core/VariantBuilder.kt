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
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.build.gradle.internal.variant.DimensionCombination
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.ManifestAttributeSupplier
import com.android.builder.core.VariantType
import com.android.builder.errors.IssueReporter
import com.android.builder.model.SourceProvider
import com.android.utils.appendCapitalized
import com.android.utils.combineAsCamelCase
import java.util.function.BooleanSupplier

/** Builder for [VariantDslInfo].
 *
 * This allows setting all temporary items on the builder before actually
 * instantiating the configuration, in order to keep it immutable.
 *
 * Use [getBuilder] as an entry point.
 */
abstract class VariantBuilder protected constructor(
    protected val dimensionCombination: DimensionCombination,
    val variantType: VariantType,
    protected val defaultConfig: DefaultConfig,
    protected val defaultSourceProvider: SourceProvider,
    protected val buildType: BuildType,
    private val buildTypeSourceProvider: SourceProvider? = null,
    protected val signingConfigOverride: SigningConfig?,
    protected val manifestAttributeSupplier: ManifestAttributeSupplier? = null,
    protected val projectOptions: ProjectOptions,
    protected val issueReporter: IssueReporter,
    protected val isInExecutionPhase: BooleanSupplier
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
            manifestAttributeSupplier: ManifestAttributeSupplier? = null,
            projectOptions: ProjectOptions,
            issueReporter: IssueReporter,
            isInExecutionPhase: BooleanSupplier

        ): VariantBuilder {
            // if this is the test module, we have a slightly different builder
            return if (variantType.isForTesting && !variantType.isTestComponent) {
                TestModuleConfigurationBuilder(
                    dimensionCombination,
                    variantType,
                    defaultConfig,
                    defaultSourceSet,
                    buildType,
                    buildTypeSourceSet,
                    signingConfigOverride,
                    manifestAttributeSupplier,
                    projectOptions,
                    issueReporter,
                    isInExecutionPhase

                )
            } else {
                VariantConfigurationBuilder(
                    dimensionCombination,
                    variantType,
                    defaultConfig,
                    defaultSourceSet,
                    buildType,
                    buildTypeSourceSet,
                    signingConfigOverride,
                    manifestAttributeSupplier,
                    projectOptions,
                    issueReporter,
                    isInExecutionPhase
                )
            }
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

    protected val flavors = mutableListOf<Pair<ProductFlavor, SourceProvider>>()

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
    abstract fun createVariantDslInfo(): VariantDslInfoImpl

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

/** Builder for non test plugin variant configurations  */
private class VariantConfigurationBuilder(
    dimensionCombination: DimensionCombination,
    variantType: VariantType,
    defaultConfig: DefaultConfig,
    defaultSourceSet: SourceProvider,
    buildType: BuildType,
    buildTypeSourceSet: SourceProvider? = null,
    signingConfigOverride: SigningConfig?,
    manifestAttributeSupplier: ManifestAttributeSupplier? = null,
    projectOptions: ProjectOptions,
    issueReporter: IssueReporter,
    isInExecutionPhase: BooleanSupplier
) : VariantBuilder(
    dimensionCombination,
    variantType,
    defaultConfig,
    defaultSourceSet,
    buildType,
    buildTypeSourceSet,
    signingConfigOverride,
    manifestAttributeSupplier,
    projectOptions,
    issueReporter,
    isInExecutionPhase
) {

    override fun createVariantDslInfo(): VariantDslInfoImpl {

        return VariantDslInfoImpl(
            ComponentIdentityImpl(
                name,
                flavorName,
                dimensionCombination.buildType,
                dimensionCombination.productFlavors
            ),
            variantType,
            defaultConfig,
            defaultSourceProvider.manifestFile,
            buildType,
            // this could be removed once the product flavor is internal only.
            flavors.map { it.first }.toImmutableList(),
            signingConfigOverride,
            manifestAttributeSupplier,
            testedVariant,
            projectOptions,
            issueReporter,
            isInExecutionPhase
        )
    }
}

/**
 * Creates a [VariantDslInfo] for a testing module variant.
 *
 *
 * The difference from the regular modules is how the original application id,
 * and application id are resolved. Our build process supports the absence of manifest
 * file for these modules, and that is why the value resolution for these attributes
 * is different.
 */
private class TestModuleConfigurationBuilder(
    dimensionCombination: DimensionCombination,
    variantType: VariantType,
    defaultConfig: DefaultConfig,
    defaultSourceSet: SourceProvider,
    buildType: BuildType,
    buildTypeSourceSet: SourceProvider? = null,
    signingConfigOverride: SigningConfig?,
    manifestAttributeSupplier: ManifestAttributeSupplier? = null,
    projectOptions: ProjectOptions,
    issueReporter: IssueReporter,
    isInExecutionPhase: BooleanSupplier
) : VariantBuilder(
    dimensionCombination,
    variantType,
    defaultConfig,
    defaultSourceSet,
    buildType,
    buildTypeSourceSet,
    signingConfigOverride,
    manifestAttributeSupplier,
    projectOptions,
    issueReporter,
    isInExecutionPhase
) {

    override fun createVariantDslInfo(): VariantDslInfoImpl {
        return object: VariantDslInfoImpl(
            ComponentIdentityImpl(
                name,
                flavorName,
                dimensionCombination.buildType,
                dimensionCombination.productFlavors
            ),
            variantType,
            defaultConfig,
            defaultSourceProvider.manifestFile,
            buildType,
            // this could be removed once the product flavor is internal only.
            flavors.map { it.first }.toImmutableList(),
            signingConfigOverride,
            manifestAttributeSupplier,
            testedVariant,
            projectOptions,
            issueReporter,
            isInExecutionPhase
        ) {
            override val applicationId: String
                get() {
                    val applicationId = mergedFlavor.testApplicationId
                    if (applicationId != null && applicationId.isNotEmpty()) {
                        return applicationId
                    }

                    return super.applicationId
                }

            override val originalApplicationId: String
                get() = applicationId

            override val testApplicationId: String
                get() = applicationId
        }
    }
}
