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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.DefaultConfig
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantProperties
import com.android.build.api.variant.impl.VariantOperations
import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.coverage.JacocoOptions
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer

/** Internal implementation of the 'new' DSL interface */
abstract class CommonExtensionImpl<
        BuildFeaturesT: BuildFeatures,
        BuildTypeT: com.android.build.api.dsl.BuildType,
        DefaultConfigT: DefaultConfig,
        ProductFlavorT: com.android.build.api.dsl.ProductFlavor,
        SigningConfigT: com.android.build.api.dsl.SigningConfig,
        VariantT: Variant<VariantPropertiesT>,
        VariantPropertiesT: VariantProperties>(
    protected val dslScope: DslScope,
    override val buildTypes: NamedDomainObjectContainer<BuildTypeT>,
    override val defaultConfig: DefaultConfigT,
    override val productFlavors: NamedDomainObjectContainer<ProductFlavorT>,
    override val signingConfigs: NamedDomainObjectContainer<SigningConfigT>
) : CommonExtension<
        BuildFeaturesT,
        BuildTypeT,
        CmakeOptions,
        CompileOptions,
        DefaultConfigT,
        ExternalNativeBuild,
        JacocoOptions,
        NdkBuildOptions,
        ProductFlavorT,
        SigningConfigT,
        TestOptions,
        TestOptions.UnitTestOptions,
        VariantT,
        VariantPropertiesT>, ActionableVariantObjectOperationsExecutor<VariantT, VariantPropertiesT> {

    fun buildFeatures(action: Action<BuildFeaturesT>) {
        action.execute(buildFeatures)
    }

    override fun buildFeatures(action: BuildFeaturesT.() -> Unit) {
        action(buildFeatures)
    }

    protected val variantOperations = VariantOperations<VariantT>()
    protected val variantPropertiesOperations = VariantOperations<VariantPropertiesT>()

    override val compileOptions: CompileOptions = dslScope.objectFactory.newInstance(CompileOptions::class.java)

    override fun compileOptions(action: CompileOptions.() -> Unit) {
        action.invoke(compileOptions)
    }

    override var compileSdkVersion: String? by dslScope.variableFactory.newProperty(null)

    override fun compileSdkVersion(version: String) {
        this.compileSdkVersion = version
    }

    override fun compileSdkVersion(apiLevel: Int) {
        compileSdkVersion("android-$apiLevel")
    }

    override fun buildTypes(action: Action<in NamedDomainObjectContainer<BuildTypeT>>) {
        action.execute(buildTypes)
    }

    override fun defaultConfig(action: Action<DefaultConfigT>) {
        action.execute(defaultConfig)
    }

    override val externalNativeBuild: ExternalNativeBuild =
        dslScope.objectFactory.newInstance(ExternalNativeBuild::class.java, dslScope)

    override fun externalNativeBuild(action: (ExternalNativeBuild) -> Unit) {
        action.invoke(externalNativeBuild)
    }

    override val jacoco: JacocoOptions = dslScope.objectFactory.newInstance(JacocoOptions::class.java)

    override fun jacoco(action: JacocoOptions.() -> Unit) {
        action.invoke(jacoco)
    }

    override fun productFlavors(action: Action<NamedDomainObjectContainer<ProductFlavorT>>) {
        action.execute(productFlavors)
    }

    override fun signingConfigs(action: Action<NamedDomainObjectContainer<SigningConfigT>>) {
        action.execute(signingConfigs)
    }

    override val testOptions: TestOptions =
        dslScope.objectFactory.newInstance(TestOptions::class.java, dslScope)

    override fun testOptions(action: TestOptions.() -> Unit) {
        action.invoke(testOptions)
    }

    override fun onVariants(action: Action<VariantT>) {
        variantOperations.actions.add(action)
    }

    override fun onVariants(action: VariantT.() -> Unit) {
        variantOperations.actions.add(Action { action.invoke(it) } )
    }

    override fun onVariantProperties(action: Action<VariantPropertiesT>) {
        variantPropertiesOperations.actions.add(action)
    }

    override fun onVariantProperties(action: (VariantPropertiesT) -> Unit) {
        variantPropertiesOperations.actions.add(Action { action.invoke(it) } )
    }

    override fun executeVariantOperations(variant: VariantT) {
        variantOperations.executeActions(variant)
    }

    override fun executeVariantPropertiesOperations(variant: VariantPropertiesT) {
        variantPropertiesOperations.executeActions(variant)
    }
}
