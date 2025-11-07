/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.core.dsl.impl

import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.dsl.VariantDimension
import com.android.build.gradle.internal.core.MergedOptions
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.manifest.ManifestData
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.variant.DimensionCombination
import com.android.build.gradle.options.BooleanOption.DEFAULT_ANDROIDX_TEST_RUNNER
import com.android.build.gradle.options.BooleanOption.USE_ANDROID_X
import com.android.builder.core.ComponentType
import com.android.builder.dexing.DexingType
import com.android.utils.appendCapitalized
import com.android.utils.combineAsCamelCase
import org.gradle.api.provider.Provider

private const val DEPRECATED_PLATFORM_TEST_RUNNER = "android.test.InstrumentationTestRunner"
private const val DEPRECATED_MULTIDEX_TEST_RUNNER =
    "com.android.test.runner.MultiDexTestRunner"
private const val ANDROIDX_TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner"

internal fun computeInstrumentationTestRunner(manifestData: Provider<ManifestData>, services: VariantServices, dexingType: DexingType): Provider<String> {
    return manifestData.zip(getDefaultInstrumentationTestRunner(services, dexingType)) { manifestData, fallback ->
        manifestData.instrumentationRunner ?: fallback
    }
}

internal fun getDefaultInstrumentationTestRunner(services: VariantServices, dexingType: DexingType): Provider<String> {
    val options = services.projectOptions
    val result = when {
        options[USE_ANDROID_X] && options[DEFAULT_ANDROIDX_TEST_RUNNER] -> ANDROIDX_TEST_RUNNER
        dexingType == DexingType.LEGACY_MULTIDEX -> DEPRECATED_MULTIDEX_TEST_RUNNER
        else -> DEPRECATED_PLATFORM_TEST_RUNNER
    }
    return services.provider { result }
}

internal const val DEFAULT_HANDLE_PROFILING = false
internal const val DEFAULT_FUNCTIONAL_TEST = false

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
@JvmOverloads
fun computeName(
    dimensionCombination: DimensionCombination,
    componentType: ComponentType,
    flavorNameCallback: ((String) -> Unit)? = null
): String {
    // compute the flavor name
    val flavorName = if (dimensionCombination.productFlavors.isEmpty()) {
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
fun computeSourceSetName(
    baseName: String,
    componentType: ComponentType
): String {
    var name = baseName
    if (name.endsWith(componentType.suffix)) {
        name = name.substring(0, name.length - componentType.suffix.length)
    }
    if (componentType.prefix.isNotEmpty()) {
        name = componentType.prefix.appendCapitalized(name)
    }
    return name
}

/**
 * Merge a specific option in GradleVariantConfiguration.
 *
 *
 * It is assumed that merged option type with a method to reset and append is created for the
 * option being merged.
 *
 *
 * The order of priority is BuildType, ProductFlavors, and default config. ProductFlavor
 * added earlier has higher priority than ProductFlavor added later.
 *
 * @param mergedOption The merged option store in the GradleVariantConfiguration.
 * @param getFlavorOption A Function to return the option from a ProductFlavor.
 * @param getBuildTypeOption A Function to return the option from a BuildType.
 * takes priority and overwrite option in the first input argument.
 * @param <CoreOptionsT> The core type of the option being merge.
 * @param <MergedOptionsT> The merge option type.
</MergedOptionsT></CoreOptionsT> */
internal fun <CoreOptionsT, MergedOptionsT : MergedOptions<CoreOptionsT>> computeMergedOptions(
    defaultConfig: DefaultConfig,
    buildTypeObj: BuildType,
    productFlavorList: List<ProductFlavor>,
    mergedOption: MergedOptionsT,
    getFlavorOption: VariantDimension.() -> CoreOptionsT?,
    getBuildTypeOption: BuildType.() -> CoreOptionsT?
) {
    mergedOption.reset()

    computeMergedOptions(
            defaultConfig,
            buildTypeObj,
            productFlavorList,
            getFlavorOption,
            getBuildTypeOption
    ) {
        mergedOption.append(it)
    }
}

internal fun <CoreOptionsT> computeMergedOptions(
        defaultConfig: DefaultConfig,
        buildTypeObj: BuildType,
        productFlavorList: List<ProductFlavor>,
        getFlavorOption: VariantDimension.() -> CoreOptionsT?,
        getBuildTypeOption: BuildType.() -> CoreOptionsT?,
        mergeAction: (CoreOptionsT) -> Unit,
) {

    defaultConfig.getFlavorOption()?.let {
        mergeAction(it)
    }
    // reverse loop for proper order
    for (i in productFlavorList.indices.reversed()) {
        productFlavorList[i].getFlavorOption()?.let {
            mergeAction(it)
        }
    }
    buildTypeObj.getBuildTypeOption()?.let {
        mergeAction(it)
    }
}
