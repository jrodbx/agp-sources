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

package com.android.build.api.component.analytics

import com.android.build.api.component.UnitTest
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.ExternalCmake
import com.android.build.api.variant.ExternalNdkBuild
import com.android.build.api.variant.Packaging
import com.android.build.api.variant.Variant
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.Serializable

abstract class AnalyticsEnabledVariant (
    override val delegate: Variant,
    stats: GradleBuildVariant.Builder,
    objectFactory: ObjectFactory
) : AnalyticsEnabledComponent(delegate, stats, objectFactory), Variant {
    override val applicationId: Provider<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.
                READ_ONLY_APPLICATION_ID_VALUE
            return delegate.applicationId
        }

    override val namespace: Provider<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.NAMESPACE_VALUE
            return delegate.namespace
        }

    override val buildConfigFields: MapProperty<String, BuildConfigField<out Serializable>>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.BUILD_CONFIG_FIELDS_VALUE
            return delegate.buildConfigFields
        }

    override fun addBuildConfigField(key: String, value: Serializable, comment: String?) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.ADD_BUILD_CONFIG_FIELD_VALUE
        delegate.addBuildConfigField(key, value, comment)
    }

    override fun addResValue(name: String, type: String, value: String, comment: String?) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.ADD_RES_VALUE_VALUE
        delegate.addResValue(name, type, value, comment)
    }

    override fun addResValue(
        name: String,
        type: String,
        value: Provider<String>,
        comment: String?
    ) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.ADD_RES_VALUE_VALUE
        delegate.addResValue(name, type, value, comment)
    }

    override val manifestPlaceholders: MapProperty<String, String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.MANIFEST_PLACEHOLDERS_VALUE
            return delegate.manifestPlaceholders
        }

    private val userVisiblePackaging: Packaging by lazy {
        objectFactory.newInstance(
            AnalyticsEnabledPackaging::class.java,
            delegate.packaging,
            stats
        )
    }

    override val packaging: Packaging
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.PACKAGING_OPTIONS_VALUE
            return userVisiblePackaging
        }

    private val userVisibleCmakeOptions: AnalyticsEnabledExternalCmake? by lazy {
        delegate.externalCmake?.let {
            objectFactory.newInstance(
                    AnalyticsEnabledExternalCmake::class.java,
                    it,
                    stats
            )
        }
    }

    override val externalCmake: ExternalCmake?
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                    VariantPropertiesMethodType.CMAKE_NATIVE_OPTIONS_VALUE
            return userVisibleCmakeOptions
        }

    private val userVisibleNdkBuildOptions: AnalyticsEnabledExternalNdkBuild? by lazy {
        delegate.externalNdkBuild?.let {
            objectFactory.newInstance(
                    AnalyticsEnabledExternalNdkBuild::class.java,
                    it,
                    stats
            )
        }
    }

    override val externalNdkBuild: ExternalNdkBuild?
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                    VariantPropertiesMethodType.NDK_BUILD_NATIVE_OPTIONS_VALUE
            return userVisibleNdkBuildOptions
        }

    override val unitTest: UnitTest?
        get() = delegate.unitTest

    override fun <T> getExtension(type: Class<T>): T? {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.GET_EXTENSION_VALUE
        return delegate.getExtension(type)
    }

    override val isPseudoLocalesEnabled: Property<Boolean>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                    VariantPropertiesMethodType.VARIANT_PSEUDOLOCALES_ENABLED_VALUE
            return delegate.isPseudoLocalesEnabled
        }

    override val proguardFiles: ListProperty<RegularFile>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.PROGUARD_FILES_VALUE
            return delegate.proguardFiles
        }
}
