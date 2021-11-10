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
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.Component
import com.android.build.api.variant.ExternalNativeBuild
import com.android.build.api.variant.ExternalNdkBuildImpl
import com.android.build.api.variant.Packaging
import com.android.build.api.variant.ResValue
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

    override val resValues: MapProperty<ResValue.Key, ResValue>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.RES_VALUE_VALUE
            return delegate.resValues
        }

    override fun makeResValueKey(type: String, name: String): ResValue.Key {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.MAKE_RES_VALUE_KEY_VALUE
        return delegate.makeResValueKey(type, name)
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

    private val userVisibleExternalNativeBuild: ExternalNativeBuild? by lazy {
        delegate.externalNativeBuild?.let {
            if (it is ExternalNdkBuildImpl) {
                objectFactory.newInstance(
                        AnalyticsEnabledExternalNdkBuild::class.java,
                        it,
                        stats
                )
            } else {
                objectFactory.newInstance(
                        AnalyticsEnabledExternalCmake::class.java,
                        it,
                        stats
                )
            }
        }
    }

    override val externalNativeBuild: ExternalNativeBuild?
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                if (userVisibleExternalNativeBuild is AnalyticsEnabledExternalNdkBuild)
                    VariantPropertiesMethodType.NDK_BUILD_NATIVE_OPTIONS_VALUE
                else
                    VariantPropertiesMethodType.CMAKE_NATIVE_OPTIONS_VALUE
            return userVisibleExternalNativeBuild
        }

    override val unitTest: UnitTest?
        get() = delegate.unitTest

    override fun <T> getExtension(type: Class<T>): T? {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.GET_EXTENSION_VALUE
        return delegate.getExtension(type)
    }

    override val pseudoLocalesEnabled: Property<Boolean>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                    VariantPropertiesMethodType.VARIANT_PSEUDOLOCALES_ENABLED_VALUE
            return delegate.pseudoLocalesEnabled
        }

    override val proguardFiles: ListProperty<RegularFile>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.PROGUARD_FILES_VALUE
            return delegate.proguardFiles
        }

    override val minSdkVersion: AndroidVersion
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.MIN_SDK_VERSION_VALUE
            return delegate.minSdkVersion
        }

    override val maxSdkVersion: Int?
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.MAX_SDK_VERSION_VALUE
            return delegate.maxSdkVersion
        }

    override val targetSdkVersion: AndroidVersion
        get()  {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.TARGET_SDK_VERSION_VALUE
            return delegate.targetSdkVersion
        }

    override val experimentalProperties: MapProperty<String, Any>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                    VariantPropertiesMethodType.VARIANT_PROPERTIES_VALUE
            return delegate.experimentalProperties
        }

    override val nestedComponents: List<Component>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.NESTED_COMPONENTS_VALUE
            return delegate.nestedComponents
        }
}
