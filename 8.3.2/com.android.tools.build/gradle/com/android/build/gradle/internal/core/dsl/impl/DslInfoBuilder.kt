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

import com.android.build.api.component.impl.ComponentIdentityImpl
import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.variant.ComponentIdentity
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.AndroidTestComponentDslInfo
import com.android.build.gradle.internal.core.dsl.ApplicationVariantDslInfo
import com.android.build.gradle.internal.core.dsl.ComponentDslInfo
import com.android.build.gradle.internal.core.dsl.DynamicFeatureVariantDslInfo
import com.android.build.gradle.internal.core.dsl.LibraryVariantDslInfo
import com.android.build.gradle.internal.core.dsl.TestFixturesComponentDslInfo
import com.android.build.gradle.internal.core.dsl.TestProjectVariantDslInfo
import com.android.build.gradle.internal.core.dsl.TestedVariantDslInfo
import com.android.build.gradle.internal.core.dsl.HostTestComponentDslInfo
import com.android.build.gradle.internal.dsl.ApplicationPublishingImpl
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.InternalApplicationExtension
import com.android.build.gradle.internal.dsl.InternalDynamicFeatureExtension
import com.android.build.gradle.internal.dsl.InternalLibraryExtension
import com.android.build.gradle.internal.dsl.InternalTestExtension
import com.android.build.gradle.internal.dsl.InternalTestedExtension
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
import com.android.builder.core.ComponentTypeImpl
import com.android.builder.model.SourceProvider
import org.gradle.api.file.DirectoryProperty

/** Builder for dsl info classes.
 *
 * This allows setting all temporary items on the builder before actually
 * instantiating the configuration, in order to keep it immutable.
 *
 * Use [getBuilder] as an entry point.
 */
class DslInfoBuilder<CommonExtensionT: CommonExtension<*, *, *, *, *, *>, DslInfoT: ComponentDslInfo> private constructor(
    private val dimensionCombination: DimensionCombination,
    val componentType: ComponentType,
    private val defaultConfig: DefaultConfig,
    private val defaultSourceProvider: SourceProvider,
    private val buildType: BuildType,
    private val buildTypeSourceProvider: SourceProvider?,
    private val signingConfigOverride: SigningConfig?,
    private val manifestDataProvider: ManifestDataProvider,
    private val variantServices: VariantServices,
    private val extension: CommonExtensionT,
    private val buildDirectory: DirectoryProperty,
) {

    companion object {
        /**
         * Returns a new builder
         */
        @JvmStatic
        fun <CommonExtensionT: CommonExtension<*, *, *, *, *, *>, DslInfoT: ComponentDslInfo> getBuilder(
            dimensionCombination: DimensionCombination,
            componentType: ComponentType,
            defaultConfig: DefaultConfig,
            defaultSourceSet: SourceProvider,
            buildType: BuildType,
            buildTypeSourceSet: SourceProvider?,
            signingConfigOverride: SigningConfig?,
            manifestDataProvider: ManifestDataProvider,
            variantServices: VariantServices,
            extension: CommonExtensionT,
            buildDirectory: DirectoryProperty,
            dslServices: DslServices
        ): DslInfoBuilder<CommonExtensionT, DslInfoT> {
            return DslInfoBuilder(
                dimensionCombination,
                componentType,
                defaultConfig,
                defaultSourceSet,
                buildType,
                buildTypeSourceSet,
                signingConfigOverride?.let { signingOverride ->
                    dslServices.newDecoratedInstance(
                        SigningConfig::class.java,
                        signingOverride.name,
                        dslServices
                    ).also {
                        it.initWith(signingOverride)
                    }
                },
                manifestDataProvider,
                variantServices,
                extension,
                buildDirectory,
            )
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
    var productionVariant: TestedVariantDslInfo? = null

    fun addProductFlavor(
        productFlavor: ProductFlavor,
        sourceProvider: SourceProvider
    ) {
        if (::variantName.isInitialized) {
            throw RuntimeException("call to getName() before calling all addProductFlavor")
        }
        flavors.add(Pair(productFlavor, sourceProvider))
    }

    private fun createComponentIdentity(): ComponentIdentity = ComponentIdentityImpl(
        name,
        flavorName,
        dimensionCombination.buildType,
        dimensionCombination.productFlavors
    )

    private fun createApplicationVariantDslInfo(): ApplicationVariantDslInfo {
        return ApplicationVariantDslInfoImpl(
            componentIdentity = createComponentIdentity(),
            componentType = componentType,
            defaultConfig = defaultConfig,
            buildTypeObj = buildType,
            productFlavorList = flavors.map { it.first },
            dataProvider = manifestDataProvider,
            services = variantServices,
            buildDirectory = buildDirectory,
            publishInfo = createPublishingInfoForApp(
                (extension as InternalApplicationExtension).publishing as ApplicationPublishingImpl,
                name,
                extension.dynamicFeatures.isNotEmpty(),
                variantServices.issueReporter
            ),
            signingConfigOverride = signingConfigOverride,
            extension = extension
        )
    }

    private fun createLibraryVariantDslInfo(): LibraryVariantDslInfo {
        return LibraryVariantDslInfoImpl(
            componentIdentity = createComponentIdentity(),
            componentType = componentType,
            defaultConfig = defaultConfig,
            buildTypeObj = buildType,
            productFlavorList = flavors.map { it.first },
            dataProvider = manifestDataProvider,
            services = variantServices,
            buildDirectory = buildDirectory,
            publishInfo = createPublishingInfoForLibrary(
                (extension as InternalLibraryExtension).publishing as LibraryPublishingImpl,
                name,
                buildType,
                flavors.map { it.first },
                extension.buildTypes,
                extension.productFlavors,
                variantServices.issueReporter
            ),
            extension = extension
        )
    }

    private fun createDynamicFeatureVariantDslInfo(): DynamicFeatureVariantDslInfo {
        return DynamicFeatureVariantDslInfoImpl(
            componentIdentity = createComponentIdentity(),
            componentType = componentType,
            defaultConfig = defaultConfig,
            buildTypeObj = buildType,
            productFlavorList = flavors.map { it.first },
            dataProvider = manifestDataProvider,
            services = variantServices,
            buildDirectory = buildDirectory,
            extension = extension as InternalDynamicFeatureExtension
        )
    }

    private fun createTestProjectVariantDslInfo(): TestProjectVariantDslInfo {
        return TestProjectVariantDslInfoImpl(
            componentIdentity = createComponentIdentity(),
            componentType = componentType,
            defaultConfig = defaultConfig,
            buildTypeObj = buildType,
            productFlavorList = flavors.map { it.first },
            dataProvider = manifestDataProvider,
            services = variantServices,
            buildDirectory = buildDirectory,
            signingConfigOverride = signingConfigOverride,
            extension = extension as InternalTestExtension
        )
    }

    private fun createTestFixturesComponentDslInfo(): TestFixturesComponentDslInfo {
        return TestFixturesDslInfoImpl(
            componentIdentity = createComponentIdentity(),
            componentType = componentType,
            defaultConfig = defaultConfig,
            buildTypeObj = buildType,
            productFlavorList = flavors.map { it.first },
            mainVariantDslInfo = productionVariant!!,
            services = variantServices,
            buildDirectory = buildDirectory,
            extension = extension
        )
    }

    private fun createUnitTestComponentDslInfo(): HostTestComponentDslInfo {
        return UnitTestComponentDslInfoImpl(
            componentIdentity = createComponentIdentity(),
            componentType = componentType,
            defaultConfig = defaultConfig,
            buildTypeObj = buildType,
            productFlavorList = flavors.map { it.first },
            services = variantServices,
            buildDirectory = buildDirectory,
            mainVariantDslInfo = productionVariant!!,
            extension = extension as InternalTestedExtension<*, *, *, *, *, *>
        )
    }

    private fun createAndroidTestComponentDslInfo(): AndroidTestComponentDslInfo {
        return AndroidTestComponentDslInfoImpl(
            componentIdentity = createComponentIdentity(),
            componentType = componentType,
            defaultConfig = defaultConfig,
            buildTypeObj = buildType,
            productFlavorList = flavors.map { it.first },
            dataProvider = manifestDataProvider,
            services = variantServices,
            buildDirectory = buildDirectory,
            mainVariantDslInfo = productionVariant!!,
            signingConfigOverride = signingConfigOverride,
            extension = extension as InternalTestedExtension<*, *, *, *, *, *>
        )
    }

    fun createDslInfo(): DslInfoT {
        return when (componentType) {
            ComponentTypeImpl.BASE_APK -> createApplicationVariantDslInfo()
            ComponentTypeImpl.LIBRARY -> createLibraryVariantDslInfo()
            ComponentTypeImpl.OPTIONAL_APK -> createDynamicFeatureVariantDslInfo()
            ComponentTypeImpl.TEST_APK -> createTestProjectVariantDslInfo()
            ComponentTypeImpl.TEST_FIXTURES -> createTestFixturesComponentDslInfo()
            ComponentTypeImpl.UNIT_TEST -> createUnitTestComponentDslInfo()
            ComponentTypeImpl.ANDROID_TEST -> createAndroidTestComponentDslInfo()
            else -> {
                throw RuntimeException("Unknown component type ${componentType.name}")
            }
        } as DslInfoT
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
