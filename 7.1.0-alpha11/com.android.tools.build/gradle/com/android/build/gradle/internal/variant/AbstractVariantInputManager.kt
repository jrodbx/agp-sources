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

import com.android.build.gradle.internal.BuildTypeData
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.core.VariantDslInfoBuilder.Companion.computeSourceSetName
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.plugins.DslContainerProvider
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.BuilderConstants
import com.android.builder.core.VariantType
import com.android.builder.core.VariantTypeImpl

/**
 * Abstract Class responsible for handling the DSL containers of flavors/build types and processing
 * them so that they can be consumed by [com.android.build.gradle.internal.VariantManager]
 *
 * This is has type parameters to handle different implementations of flavors and build types,
 * whether this is legacy or new per-plugin-type versions.
 *
 * This instantiate the containers and makes them available to the extension classes via
 * implementing [DslContainerProvider].
 *
 * This setups container call back to process new values and validates them.
 *
 * Then, this exposes the results by implementing [VariantInputModel].
 *
 * This class does everything, except actually instantiating the containers as this needs
 * to be done per flavor/build-type type. This is handled by children classes for each use case.
 */
abstract class AbstractVariantInputManager<
        DefaultConfigT : com.android.build.api.dsl.DefaultConfig,
        BuildTypeT : com.android.build.api.dsl.BuildType,
        ProductFlavorT : com.android.build.api.dsl.ProductFlavor,
        SigningConfigT : com.android.build.api.dsl.ApkSigningConfig>(
            private val dslServices: DslServices,
            private val variantType: VariantType,
            override val sourceSetManager: SourceSetManager
        ) : VariantInputModel<DefaultConfigT, BuildTypeT, ProductFlavorT, SigningConfigT>,
    DslContainerProvider<DefaultConfigT, BuildTypeT, ProductFlavorT, SigningConfigT> {

    override val buildTypes = mutableMapOf<String, BuildTypeData<BuildTypeT>>()
    override val productFlavors = mutableMapOf<String, ProductFlavorData<ProductFlavorT>>()
    override val signingConfigs = mutableMapOf<String, SigningConfigT>()

    protected fun addSigningConfig(signingConfig: SigningConfigT) {
        signingConfigs[signingConfig.name] = signingConfig
    }

    /**
     * Adds new BuildType, creating a BuildTypeData, and the associated source set, and adding it to
     * the map.
     *
     * @param buildType the build type.
     */
    protected fun addBuildType(buildType: BuildTypeT) {
        val name = buildType.name
        checkName(name, "BuildType")
        if (productFlavors.containsKey(name)) {
            throw RuntimeException("BuildType names cannot collide with ProductFlavor names")
        }

        // FIXME update when we have the newer interfaces for BuildTypes.
        if (buildType is BuildType) {
            if (variantType.isDynamicFeature) {
                // initialize it without the signingConfig for dynamic-features.
                buildType.init()
            } else {
                buildType.init(signingConfigContainer.findByName(BuilderConstants.DEBUG) as SigningConfig)
            }
        } else {
            throw RuntimeException("Unexpected instance of BuildTypeT")
        }

        // always create the android Test source set even if this is not the buildType that is
        // tested. this is because we cannot delay creation without breaking compatibility.
        // So for now we create more than we need and we'll migrate to a better way later.
        // FIXME b/149489432
        val androidTestSourceSet = if (variantType.hasTestComponents) {
            sourceSetManager.setUpTestSourceSet(
                computeSourceSetName(
                    buildType.name, VariantTypeImpl.ANDROID_TEST
                )
            ) as DefaultAndroidSourceSet
        } else null

        val unitTestSourceSet = if (variantType.hasTestComponents) {
            sourceSetManager.setUpTestSourceSet(
                computeSourceSetName(
                    buildType.name, VariantTypeImpl.UNIT_TEST
                )
            ) as DefaultAndroidSourceSet
        } else null

        val testFixturesSourceSet =
            if (variantType.hasTestComponents) {
                sourceSetManager.setUpSourceSet(
                    computeSourceSetName(
                        buildType.name, VariantTypeImpl.TEST_FIXTURES
                    )
                ) as DefaultAndroidSourceSet
            } else null

        buildTypes[name] = BuildTypeData(
            buildType = buildType,
            sourceSet = sourceSetManager.setUpSourceSet(buildType.name) as DefaultAndroidSourceSet,
            testFixturesSourceSet = testFixturesSourceSet,
            androidTestSourceSet = androidTestSourceSet,
            unitTestSourceSet = unitTestSourceSet
        )
    }

    /**
     * Adds a new ProductFlavor, creating a ProductFlavorData and associated source sets, and adding
     * it to the map.
     *
     * @param productFlavor the product flavor
     */
    protected fun addProductFlavor(productFlavor: ProductFlavorT) {
        val name = productFlavor.name
        checkName(name, "ProductFlavor")
        if (buildTypes.containsKey(name)) {
            throw RuntimeException("ProductFlavor names cannot collide with BuildType names")
        }
        val mainSourceSet =
            sourceSetManager.setUpSourceSet(productFlavor.name) as DefaultAndroidSourceSet
        var testFixturesSourceSet: DefaultAndroidSourceSet? = null
        var androidTestSourceSet: DefaultAndroidSourceSet? = null
        var unitTestSourceSet: DefaultAndroidSourceSet? = null
        if (variantType.hasTestComponents) {
            androidTestSourceSet = sourceSetManager.setUpTestSourceSet(
                computeSourceSetName(
                    productFlavor.name, VariantTypeImpl.ANDROID_TEST
                )
            ) as DefaultAndroidSourceSet
            unitTestSourceSet = sourceSetManager.setUpTestSourceSet(
                computeSourceSetName(
                    productFlavor.name, VariantTypeImpl.UNIT_TEST
                )
            ) as DefaultAndroidSourceSet
            testFixturesSourceSet = sourceSetManager.setUpSourceSet(
                computeSourceSetName(
                    productFlavor.name, VariantTypeImpl.TEST_FIXTURES
                )
            ) as DefaultAndroidSourceSet
        }
        val productFlavorData =
            ProductFlavorData(
                productFlavor = productFlavor,
                sourceSet = mainSourceSet,
                testFixturesSourceSet = testFixturesSourceSet,
                androidTestSourceSet = androidTestSourceSet,
                unitTestSourceSet = unitTestSourceSet
            )
        productFlavors[productFlavor.name] = productFlavorData
    }

    companion object {
        private fun checkName(name: String, displayName: String) {
            checkPrefix(
                name,
                displayName,
                VariantType.ANDROID_TEST_PREFIX
            )
            checkPrefix(
                name,
                displayName,
                VariantType.UNIT_TEST_PREFIX
            )
            if (BuilderConstants.LINT == name) {
                throw RuntimeException(
                    String.format(
                        "%1\$s names cannot be %2\$s",
                        displayName,
                        BuilderConstants.LINT
                    )
                )
            }
        }

        private fun checkPrefix(
            name: String,
            displayName: String,
            prefix: String
        ) {
            if (name.startsWith(prefix)) {
                throw RuntimeException(
                    String.format(
                        "%1\$s names cannot start with '%2\$s'",
                        displayName,
                        prefix
                    )
                )
            }
        }
    }
}
