/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.api.component.impl

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.AndroidTestProperties
import com.android.build.api.component.ComponentIdentity
import com.android.build.api.variant.AaptOptions
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.impl.ResValue
import com.android.build.api.variant.impl.VariantPropertiesImpl
import com.android.build.api.variant.impl.initializeAaptOptionsFromDsl
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.Serializable
import javax.inject.Inject

open class AndroidTestPropertiesImpl @Inject constructor(
    componentIdentity: ComponentIdentity,
    buildFeatureValues: BuildFeatureValues,
    variantDslInfo: VariantDslInfo,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    variantScope: VariantScope,
    variantData: BaseVariantData,
    testedVariant: VariantPropertiesImpl,
    transformManager: TransformManager,
    variantPropertiesApiServices: VariantPropertiesApiServices,
    taskCreationServices: TaskCreationServices,
    globalScope: GlobalScope
) : TestComponentPropertiesImpl(
    componentIdentity,
    buildFeatureValues,
    variantDslInfo,
    variantDependencies,
    variantSources,
    paths,
    artifacts,
    variantScope,
    variantData,
    testedVariant,
    transformManager,
    variantPropertiesApiServices,
    taskCreationServices,
    globalScope
), AndroidTestProperties, AndroidTestCreationConfig {

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val debuggable: Boolean
        get() = variantDslInfo.isDebuggable

    override val applicationId: Property<String> = internalServices.propertyOf(
        String::class.java,
        variantDslInfo.applicationId)

    override val manifestPlaceholders: MapProperty<String, String> by lazy {
        internalServices.mapPropertyOf(
            String::class.java,
            String::class.java,
            variantDslInfo.manifestPlaceholders,
            "$name:manifestPlaceholders"
        )
    }

    override val aaptOptions: AaptOptions by lazy {
        initializeAaptOptionsFromDsl(
            globalScope.extension.aaptOptions,
            variantPropertiesApiServices
        )
    }

    override fun aaptOptions(action: AaptOptions.() -> Unit) {
        action.invoke(aaptOptions)
    }

    override val instrumentationRunner: Property<String> =
        internalServices.propertyOf(String::class.java, variantDslInfo.instrumentationRunner)

    override val handleProfiling: Property<Boolean> =
        internalServices.propertyOf(Boolean::class.java, variantDslInfo.handleProfiling)

    override val functionalTest: Property<Boolean> =
        internalServices.propertyOf(Boolean::class.java, variantDslInfo.functionalTest)

    override val testLabel: Property<String?> =
        internalServices.nullablePropertyOf(String::class.java, variantDslInfo.testLabel)

    override val buildConfigFields: MapProperty<String, BuildConfigField<out Serializable>> by lazy {
        internalServices.mapPropertyOf(
            String::class.java,
            BuildConfigField::class.java,
            variantDslInfo.getBuildConfigFields(),
            "$name:buildConfigs"
        )
    }

    /**
     * Adds a ResValue element to the generated resources.
     * @param name the resource name
     * @param type the resource type like 'string'
     * @param value the resource value
     * @param comment optional comment to be added to the generated resource file for the field.
     */
    override fun addResValue(name: String, type: String, value: String, comment: String?) {
        resValues.put(ResValue.Key(type, name), ResValue(value, comment))
    }

    /**
     * Adds a ResValue element to the generated resources.
     * @param name the resource name
     * @param type the resource type like 'string'
     * @param value a [Provider] for the value
     * @param comment optional comment to be added to the generated resource file for the field.
     */
    override fun addResValue(name: String, type: String, value: Provider<String>, comment: String?) {
        resValues.put(ResValue.Key(type, name), value.map { ResValue(it, comment) })
    }

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    // always false for this type
    override val embedsMicroApp: Boolean
        get() = false

    // always true for this kind
    override val testOnlyApk: Boolean
        get() = true

    override val testedApplicationId: Provider<String>
        get() = if (testedConfig.variantType.isAar) {
            // if the tested variant is an AAR, the test is self contained and therefore
            // testedAppID == appId
            applicationId
        } else {
            testedConfig.applicationId
        }

    override val instrumentationRunnerArguments: Map<String, String>
        get() = variantDslInfo.instrumentationRunnerArguments

    override val isTestCoverageEnabled: Boolean
        get() = variantDslInfo.isTestCoverageEnabled

    override val resValues: MapProperty<ResValue.Key, ResValue> by lazy {
        internalServices.mapPropertyOf(
            ResValue.Key::class.java,
            ResValue::class.java,
            variantDslInfo.getResValues(),
            "$name:resValues"
        )
    }

    /**
     * Package desugar_lib DEX for base feature androidTest only if the base packages shrunk
     * desugar_lib. This should be fixed properly by analyzing the test code when generating L8
     * keep rules, and thus packaging desugar_lib dex in the tested APK only which contains
     * necessary classes used both in test and tested APKs.
     */
    override val shouldPackageDesugarLibDex: Boolean
        get() = when {
            !variantScope.isCoreLibraryDesugaringEnabled -> false
            testedConfig.variantType.isAar -> true
            else -> testedConfig.variantType.isBaseModule && testedConfig.variantScope.needsShrinkDesugarLibrary
        }
}

