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
package com.android.build.api.variant.impl

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.Component
import com.android.build.api.component.impl.ComponentImpl
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.variant.*
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.builder.core.VariantType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.objectweb.asm.Type
import java.io.Serializable

abstract class VariantImpl(
    override val variantBuilder: VariantBuilderImpl,
    buildFeatureValues: BuildFeatureValues,
    variantDslInfo: VariantDslInfo,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    variantScope: VariantScope,
    variantData: BaseVariantData,
    transformManager: TransformManager,
    variantPropertiesApiServices: VariantPropertiesApiServices,
    taskCreationServices: TaskCreationServices,
    globalScope: GlobalScope
) : ComponentImpl(
    variantBuilder,
    buildFeatureValues,
    variantDslInfo,
    variantDependencies,
    variantSources,
    paths,
    artifacts,
    variantScope,
    variantData,
    transformManager,
    variantPropertiesApiServices,
    taskCreationServices,
    globalScope
), Variant, ConsumableCreationConfig {

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val buildConfigFields: MapProperty<String, BuildConfigField<out Serializable>> by lazy {
        internalServices.mapPropertyOf(
            String::class.java,
            BuildConfigField::class.java,
            variantDslInfo.getBuildConfigFields()
        )
    }

    override fun addBuildConfigField(key: String, value: Serializable, comment: String?) {
        val descriptor = Type.getDescriptor(value::class.java)
                .removeSurrounding("Ljava/lang/", ";")
        buildConfigFields.put(key, BuildConfigField(descriptor, value, comment))
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

    override val manifestPlaceholders: MapProperty<String, String> by lazy {
        @Suppress("UNCHECKED_CAST")
        internalServices.mapPropertyOf(
            String::class.java,
            String::class.java,
            variantDslInfo.manifestPlaceholders
        )
    }

    override val packaging: Packaging by lazy {
        PackagingImpl(globalScope.extension.packagingOptions, internalServices)
    }


    override val externalCmake: ExternalCmake? by lazy {
        variantDslInfo.externalNativeBuildOptions.externalNativeCmakeOptions?.let {
            ExternalCmakeImpl(
                    it,
                    variantPropertiesApiServices
            )
        }
    }

    override val externalNdkBuild: ExternalNdkBuild? by lazy {
        variantDslInfo.externalNativeBuildOptions.externalNativeNdkBuildOptions?.let {
            ExternalNdkBuildImpl(
                    it,
                    variantPropertiesApiServices
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    val testComponents = mutableMapOf<VariantType, ComponentImpl>()

    override val resValues: MapProperty<ResValue.Key, ResValue> by lazy {
        internalServices.mapPropertyOf(
            ResValue.Key::class.java,
            ResValue::class.java,
            variantDslInfo.getResValues()
        )
    }

    override val renderscriptTargetApi: Int
        get() =  variantBuilder.renderscriptTargetApi

    override val minSdkVersion: AndroidVersion
        get() = variantBuilder.minSdkVersion

    override val maxSdkVersion: Int?
        get() = variantBuilder.maxSdkVersion

    override val targetSdkVersion: AndroidVersion
        get() = variantBuilder.targetSdkVersion

    private var _isMultiDexEnabled: Boolean? = variantDslInfo.isMultiDexEnabled
    override val isMultiDexEnabled: Boolean
        get() {
            return _isMultiDexEnabled ?: (minSdkVersion.getFeatureLevel() >= 21)
        }

    private val isBaseModule = variantDslInfo.variantType.isBaseModule

    override val needsMainDexListForBundle: Boolean
        get() = isBaseModule
                && globalScope.hasDynamicFeatures()
                && dexingType.needsMainDexList

    // TODO: Move down to lower type and remove from VariantScope.
    override val isCoreLibraryDesugaringEnabled: Boolean
        get() = variantScope.isCoreLibraryDesugaringEnabled(this)

    abstract override fun <T : Component> createUserVisibleVariantObject(
            projectServices: ProjectServices,
            operationsRegistrar: VariantApiOperationsRegistrar<VariantBuilder, Variant>,
            stats: GradleBuildVariant.Builder?): T
}
