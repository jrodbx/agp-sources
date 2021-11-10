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

package com.android.build.gradle.internal.variant

import com.android.build.api.component.impl.ComponentImpl
import com.android.build.api.component.impl.TestComponentImpl
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.builder.errors.IssueReporter
import com.google.common.base.Joiner
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ImmutableMap
import java.util.ArrayList
import java.util.Comparator

class VariantModelImpl(
    override val inputs: VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>,
    private val testBuilderTypeProvider: () -> String?,
    private val variantProvider: () -> List<VariantImpl>,
    private val testComponentProvider: () -> List<TestComponentImpl>,
    private val issueHandler: IssueReporter) : VariantModel {

    override val variants: List<VariantImpl>
        get() = variantProvider()

    override val testComponents: List<TestComponentImpl>
        get() = testComponentProvider()

    override val defaultVariant: String?
        get() = computeDefaultVariant()

    /**
     * Calculates the default variant to put in the model.
     *
     *
     * Given user preferences, this attempts to respect them in the presence of the variant
     * filter.
     *
     *
     * This prioritizes by, in descending order of preference:
     *
     *
     *  - The build author's explicit build type settings
     *  - The build author's explicit product flavor settings, matching the highest number of
     * chosen defaults
     *  - The fallback default build type, which is the tested build type, if applicable,
     * otherwise 'debug'
     *  - The alphabetically sorted default product flavors, left to right
     *
     *
     * @return the name of a variant that exists under the presence of the variant filter. Only
     * returns null if all variants are removed.
     */
    private fun computeDefaultVariant(): String? {
        // Finalize the DSL we are about to read.
        finalizeDefaultVariantDsl()

        // Exit early if all variants were filtered out, this is not a valid project
        if (variants.isEmpty()) {
            return null
        }
        // Otherwise get the 'best' build type, respecting the user's preferences first.
        val chosenBuildType: String? = getBuildAuthorSpecifiedDefaultBuildType()
        val chosenFlavors: Map<String, String> = getBuildAuthorSpecifiedDefaultFlavors()
        val fallbackDefaultBuildType: String = testBuilderTypeProvider() ?: "debug"
        val preferredDefaultVariantScopeComparator: Comparator<ComponentImpl> =
            BuildAuthorSpecifiedDefaultBuildTypeComparator(chosenBuildType)
                .thenComparing(BuildAuthorSpecifiedDefaultsFlavorComparator(chosenFlavors))
                .thenComparing(DefaultBuildTypeComparator(fallbackDefaultBuildType))
                .thenComparing(DefaultFlavorComparator())

        // Ignore test, base feature and feature variants.
        // * Test variants have corresponding production variants
        // * Hybrid feature variants have corresponding library variants.
        val defaultComponent: VariantImpl? = variants.minWith(preferredDefaultVariantScopeComparator)

        return defaultComponent?.name
    }

    /** Prevent any subsequent modifications to the default variant DSL properties.  */
    private fun finalizeDefaultVariantDsl() {
        for (buildTypeData in inputs.buildTypes.values) {
            buildTypeData.buildType.getIsDefault().finalizeValue()
        }
        for (productFlavorData in inputs.productFlavors.values) {
            productFlavorData.productFlavor.getIsDefault().finalizeValue()
        }
    }


    /**
     * Computes explicit build-author default build type.
     *
     * @return user specified default build type, null if none set.
     */
    private fun getBuildAuthorSpecifiedDefaultBuildType(): String? {
        // First look for the user setting
        val buildTypesMarkedAsDefault: MutableList<String> = ArrayList(1)
        for (buildType in inputs.buildTypes.values) {
            if (buildType.buildType.isDefault) {
                buildTypesMarkedAsDefault.add(buildType.buildType.name)
            }
        }

        buildTypesMarkedAsDefault.sort()

        if (buildTypesMarkedAsDefault.size > 1) {
            issueHandler.reportWarning(
                IssueReporter.Type.AMBIGUOUS_BUILD_TYPE_DEFAULT,
                "Ambiguous default build type: '"
                        + Joiner.on("', '").join(buildTypesMarkedAsDefault)
                        + "'.\n"
                        + "Please only set `isDefault = true` for one build type.",
                Joiner.on(',').join(buildTypesMarkedAsDefault)
            )
        }

        return if (buildTypesMarkedAsDefault.isEmpty()) {
            null
        } else {
            // This picks the first alphabetically that was tagged, to make it stable,
            // even if the user accidentally tags two build types as default.
            buildTypesMarkedAsDefault[0]
        }
    }

    /**
     * Computes explicit user set default product flavors for each dimension.
     *
     * @param syncIssueHandler any configuration issues will be added here, e.g. if multiple flavors
     * in one dimension are marked as default.
     * @return map from flavor dimension to the user-specified default flavor for that dimension,
     * with entries missing for flavors without user-specified defaults.
     */
    private fun getBuildAuthorSpecifiedDefaultFlavors(): Map<String, String> {
        // Using ArrayListMultiMap to preserve sorting of flavor names.
        val userDefaults = ArrayListMultimap.create<String, String>()

        for (flavor in inputs.productFlavors.values) {
            val productFlavor = flavor.productFlavor
            val dimension = productFlavor.dimension

            @Suppress("DEPRECATION")
            if (productFlavor.getIsDefault().get()) {
                userDefaults.put(dimension, productFlavor.name)
            }
        }
        val defaults = ImmutableMap.builder<String, String>()

        // For each user preference, validate it and override the alphabetical default.
        for (dimension in userDefaults.keySet()) {
            val userDefault = userDefaults[dimension]

            userDefault.sort()

            if (userDefault.isNotEmpty()) {
                // This picks the first alphabetically that was tagged, to make it stable,
                // even if the user accidentally tags two flavors in the same dimension as default.
                defaults.put(dimension, userDefault[0])
            }

            // Report the ambiguous default setting.
            if (userDefault.size > 1) {
                    issueHandler.reportWarning(
                                IssueReporter.Type.AMBIGUOUS_PRODUCT_FLAVOR_DEFAULT,
"""Ambiguous default product flavors for flavor dimension '$dimension': '${Joiner.on("', '").join(userDefault)}'.
Please only set `isDefault = true` for one product flavor in each flavor dimension.""",
                                dimension
                            )
            }
        }
        return defaults.build()
    }
}

/**
 * Compares variants prioritizing those that match the given default build type.
 *
 *
 * The best match is the *minimum* element.
 *
 *
 * Note: this comparator imposes orderings that are inconsistent with equals, as variants
 * that do not match the default will compare the same.
 */
private class BuildAuthorSpecifiedDefaultBuildTypeComparator constructor(
    private val chosen: String?
) : Comparator<ComponentImpl> {
    override fun compare(v1: ComponentImpl, v2: ComponentImpl): Int {
        if (chosen == null) {
            return 0
        }
        val b1Score = if (v1.buildType == chosen) 1 else 0
        val b2Score = if (v2.buildType == chosen) 1 else 0
        return b2Score - b1Score
    }

}

/**
 * Compares variants prioritizing those that match the given default flavors over those that do
 * not.
 *
 *
 * The best match is the *minimum* element.
 *
 *
 * Note: this comparator imposes orderings that are inconsistent with equals, as variants
 * that do not match the default will compare the same.
 */
private class BuildAuthorSpecifiedDefaultsFlavorComparator constructor(
    private val defaultFlavors: Map<String, String>
) : Comparator<ComponentImpl> {
    override fun compare(v1: ComponentImpl, v2: ComponentImpl): Int {
        var f1Score = 0
        var f2Score = 0
        for (flavor in v1.variantDslInfo.productFlavorList) {
            if (flavor.name == defaultFlavors[flavor.dimension]) {
                f1Score++
            }
        }
        for (flavor in v2.variantDslInfo.productFlavorList) {
            if (flavor.name == defaultFlavors[flavor.dimension]) {
                f2Score++
            }
        }
        return f2Score - f1Score
    }

}

/**
 * Compares variants on build types.
 *
 *
 * Prefers 'debug', then falls back to the first alphabetically.
 *
 *
 * The best match is the *minimum* element.
 */
private class DefaultBuildTypeComparator constructor(
    private val preferredBuildType: String
) : Comparator<ComponentImpl> {
    override fun compare(v1: ComponentImpl, v2: ComponentImpl): Int {
        val b1 = v1.buildType
        val b2 = v2.buildType
        return if (b1 == b2) {
            0
        } else if (b1 == preferredBuildType) {
            -1
        } else if (b2 == preferredBuildType) {
            1
        } else {
            b1!!.compareTo(b2!!)
        }
    }
}

/**
 * Compares variants prioritizing product flavors alphabetically, left-to-right.
 *
 *
 * The best match is the *minimum* element.
 */
private class DefaultFlavorComparator : Comparator<ComponentImpl> {
    override fun compare(v1: ComponentImpl, v2: ComponentImpl): Int {
        // Compare flavors left-to right.
        for (i in v1.variantDslInfo.productFlavorList.indices) {
            val f1 = v1.variantDslInfo.productFlavorList[i].name
            val f2 = v2.variantDslInfo.productFlavorList[i].name
            val diff = f1.compareTo(f2)
            if (diff != 0) {
                return diff
            }
        }
        return 0
    }
}
