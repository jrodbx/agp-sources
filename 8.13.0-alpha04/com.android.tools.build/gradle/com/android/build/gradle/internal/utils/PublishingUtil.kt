/*
 * Copyright (C) 2021 The Android Open Source Project
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

@file:JvmName("PublishingUtils")

package com.android.build.gradle.internal.utils

import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.AbstractPublishing
import com.android.build.gradle.internal.dsl.ApplicationPublishingImpl
import com.android.build.gradle.internal.dsl.LibraryPublishingImpl
import com.android.build.gradle.internal.dsl.MultipleVariantsImpl
import com.android.build.gradle.internal.publishing.ComponentPublishingInfo
import com.android.build.gradle.internal.publishing.VariantPublishingInfo
import com.android.builder.errors.IssueReporter
import org.gradle.api.NamedDomainObjectContainer

fun createPublishingInfoForLibrary(
    publishing: LibraryPublishingImpl,
    variantName: String,
    buildType: BuildType,
    flavorList: List<ProductFlavor>,
    buildTypes: NamedDomainObjectContainer<out BuildType>,
    productFlavors: NamedDomainObjectContainer<out ProductFlavor>,
    issueReporter: IssueReporter
): VariantPublishingInfo {
    val components = mutableListOf<ComponentPublishingInfo>()

    ensureComponentNameUniqueness(publishing, issueReporter)

    val singleVariant = publishing.singleVariants.find {
            it.variantName == variantName
    }
    if (singleVariant != null) {
        components.add(ComponentPublishingInfo(
            variantName,
            AbstractPublishing.Type.AAR,
            withSourcesJar = singleVariant.withSourcesJar,
            withJavadocJar = singleVariant.withJavadocJar
        ))
    }

    for (multipleVariant in publishing.multipleVariantsContainer) {
        ensureUsersInputCorrectness(multipleVariant, buildTypes, productFlavors, issueReporter)

        val buildTypeAttribute = computeBuildTypeAttribute(multipleVariant, buildType)
        val flavorDimensionAttributes =
            computeFlavorDimensionAttribute(multipleVariant, flavorList)

        val isClassifierRequired = buildTypeAttribute != null || flavorDimensionAttributes.isNotEmpty()

        val publishThisVariant = multipleVariant.allVariants
            || isVariantSelectedExplicitly(multipleVariant, buildType, flavorList)

        if (publishThisVariant) {
            components.add(ComponentPublishingInfo(
                multipleVariant.componentName,
                AbstractPublishing.Type.AAR,
                ComponentPublishingInfo.AttributesConfig(
                    buildTypeAttribute,
                    flavorDimensionAttributes
                ),
                isClassifierRequired,
                multipleVariant.withSourcesJar,
                multipleVariant.withJavadocJar
            ))
        }
    }
    return VariantPublishingInfo(components)
}

private fun ensureUsersInputCorrectness(
    multipleVariant: MultipleVariantsImpl,
    buildTypes: NamedDomainObjectContainer<out BuildType>,
    productFlavors: NamedDomainObjectContainer<out ProductFlavor>,
    issueReporter: IssueReporter
) {
    fun computeErrorMessage(element: String) =
        "Using non-existing $element when selecting variants to be published in " +
                "multipleVariants publishing DSL."

    val allBuildTypes = buildTypes.map { it.name }.toSet()
    for (includeBuildType in multipleVariant.includedBuildTypes) {
        if (!allBuildTypes.contains(includeBuildType)) {
            issueReporter.reportError(
                IssueReporter.Type.GENERIC,
                computeErrorMessage("build type \"$includeBuildType\"")
            )
        }
    }

    for (entry in multipleVariant.includedFlavorDimensionAndValues.entries) {
        val flavorsWithSpecifiedDimension = productFlavors.filter { it.dimension == entry.key }
        if (flavorsWithSpecifiedDimension.isEmpty()) {
            issueReporter.reportError(
                IssueReporter.Type.GENERIC,
                computeErrorMessage("dimension \"${entry.key}\"")
            )
        }
        val allFlavorValues = flavorsWithSpecifiedDimension.map { it.name }.toSet()
        for (flavorValue in entry.value) {
            if (!allFlavorValues.contains(flavorValue)) {
                issueReporter.reportError(
                    IssueReporter.Type.GENERIC,
                    computeErrorMessage("flavor value \"$flavorValue\"")
                )
            }
        }
    }
}

private fun computeBuildTypeAttribute(
    multipleVariant : MultipleVariantsImpl,
    buildType: BuildType
): String? {
    return if (multipleVariant.includedBuildTypes.size > 1 || multipleVariant.allVariants)
        buildType.name
    else
        null
}

private fun computeFlavorDimensionAttribute(
    multipleVariant : MultipleVariantsImpl,
    allFlavors: List<ProductFlavor>
): MutableSet<String> {
    val flavorDimensionAttributes = mutableSetOf<String>()
    if (multipleVariant.allVariants) {
        allFlavors.map {
            flavorDimensionAttributes.add(it.dimension!!)
        }
    } else {
        for (entry in multipleVariant.includedFlavorDimensionAndValues.entries) {
            if (entry.value.size > 1) {
                flavorDimensionAttributes.add(entry.key)
            }
        }
    }

    return flavorDimensionAttributes
}

private fun isVariantSelectedExplicitly(
    multipleVariant : MultipleVariantsImpl,
    buildType: BuildType,
    flavorList: List<ProductFlavor>,
): Boolean {
    if (!multipleVariant.includedBuildTypes.contains(buildType.name)) {
        return false
    }
    for (productFlavor in flavorList) {
        val dimensionValue = multipleVariant.includedFlavorDimensionAndValues[productFlavor.dimension]
        if (dimensionValue == null || !dimensionValue.contains(productFlavor.name)) {
            return false
        }
    }
    return true
}

private fun ensureComponentNameUniqueness(
    publishing: LibraryPublishingImpl,
    issueReporter: IssueReporter
) {
    val singleVariantPubComponents = publishing.singleVariants.map { it.variantName }.toSet()
    publishing.multipleVariantsContainer.map {
        if (singleVariantPubComponents.contains(it.componentName)) {
            issueReporter.reportError(
                IssueReporter.Type.GENERIC,
                "Publishing variants to the \"${it.componentName}\" component using both" +
                        " singleVariant and multipleVariants publishing DSL is not allowed."
            )
        }
    }
}

fun createPublishingInfoForApp(
    publishing: ApplicationPublishingImpl,
    variantName: String,
    hasDynamicFeatures: Boolean,
    issueReporter: IssueReporter
): VariantPublishingInfo {
    val components = mutableListOf<ComponentPublishingInfo>()
    publishing.singleVariants.find { it.variantName == variantName }?.let {
        if (it.publishVariantAsApk) {
            // do not publish the APK(s) if there are dynamic feature.
            if (hasDynamicFeatures) {
                issueReporter.reportError(
                    IssueReporter.Type.GENERIC,
                    "When dynamic feature modules exist, publishing APK is not allowed."
                )
            } else {
                components.add(
                    ComponentPublishingInfo(variantName, AbstractPublishing.Type.APK))
            }
        } else {
            components.add(
                ComponentPublishingInfo(variantName, AbstractPublishing.Type.AAB))
        }
    }
    return VariantPublishingInfo(components)
}
