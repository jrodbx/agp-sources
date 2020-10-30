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

import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.PackagingOptions
import com.android.build.api.variant.VariantProperties
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import java.io.Serializable

abstract class AnalyticsEnabledVariantProperties (
    override val delegate: VariantProperties,
    stats: GradleBuildVariant.Builder,
    objectFactory: ObjectFactory
) : AnalyticsEnabledComponentProperties(delegate, stats, objectFactory), VariantProperties {
    override val applicationId: Provider<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.
                READ_ONLY_APPLICATION_ID_VALUE
            return delegate.applicationId
        }

    override val packageName: Provider<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.PACKAGE_NAME_VALUE
            return delegate.packageName
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

    override val packagingOptions: PackagingOptions
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.PACKAGING_OPTIONS_VALUE
            return delegate.packagingOptions
        }
}
