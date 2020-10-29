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

import com.android.build.api.artifact.Operations
import com.android.build.api.component.ComponentIdentity
import com.android.build.api.component.ComponentProperties
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.impl.GradleProperty
import com.android.build.api.variant.impl.VariantOutputConfigurationImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.api.variant.impl.VariantOutputList
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.scope.ApkData
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import org.gradle.api.provider.Property

abstract class ComponentPropertiesImpl(
    val dslScope: DslScope,
    val variantScope: VariantScope,
    override val operations: Operations,
    configuration: ComponentIdentity
): ComponentProperties, ComponentIdentity by configuration {

    protected val variantDslInfo = variantScope.variantDslInfo

    private val variantOutputs= mutableListOf<VariantOutputImpl>()

    override val outputs: VariantOutputList
        get() = VariantOutputList(variantOutputs.toList())

    override val applicationId: Property<String> = dslScope.objectFactory.property(String::class.java).apply {
        setDisallowChanges(dslScope.providerFactory.provider { variantDslInfo.applicationId })
    }

    fun addVariantOutput(apkData: ApkData): VariantOutputImpl {
        // the DSL objects are now locked, if the versionCode is provided, use that
        // otherwise use the lazy manifest reader to extract the value from the manifest
        // file.
        val versionCode = variantDslInfo.getVersionCode(true)
        val versionCodeProperty = initializeProperty(Int::class.java, "$name::versionCode")
        if (versionCode <= 0) {
            versionCodeProperty.set(
                dslScope.providerFactory.provider {
                    variantDslInfo.manifestVersionCodeSupplier.asInt
                })
        } else {
            versionCodeProperty.set(versionCode)
        }
        // the DSL objects are now locked, if the versionName is provided, use that; otherwise use
        // the lazy manifest reader to extract the value from the manifest file.
        val versionName = variantDslInfo.getVersionName(true)
        val versionNameProperty = initializeProperty(String::class.java, "$name::versionName")
        versionNameProperty.set(
            dslScope.providerFactory.provider {
                versionName ?: variantDslInfo.manifestVersionNameSupplier.get()
            }
        )
        val variantOutputConfiguration = VariantOutputConfigurationImpl(
            apkData.isUniversal,
            apkData.filters.map { filterData ->
                FilterConfiguration(
                    FilterConfiguration.FilterType.valueOf(filterData.filterType),
                    filterData.identifier)
            }
        )
        return VariantOutputImpl(
            versionCodeProperty,
            versionNameProperty,
            initializeProperty(Boolean::class.java, "$name::isEnabled").value(true),
            variantOutputConfiguration,
            apkData
        ).also {
            apkData.variantOutput = it
            variantOutputs.add(it)
        }
    }

    protected fun <T> initializeProperty(type: Class<T>, id: String): Property<T> {
        return if (variantScope.globalScope.projectOptions[BooleanOption.USE_SAFE_PROPERTIES]) {
            GradleProperty.safeReadingBeforeExecution(id, dslScope.objectFactory.property(type))
        } else {
            dslScope.objectFactory.property(type)
        }
    }

}