/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.api.variant.AarMetadata
import com.android.build.api.variant.ResValue
import com.android.build.api.variant.TestFixtures
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject

open class AnalyticsEnabledTestFixtures @Inject constructor(
    override val delegate: TestFixtures,
    stats: GradleBuildVariant.Builder,
    objectFactory: ObjectFactory
) : AnalyticsEnabledComponent(
    delegate, stats, objectFactory
), TestFixtures {
    private val userVisibleAarMetadata: AarMetadata by lazy {
        objectFactory.newInstance(
            AnalyticsEnabledAarMetadata::class.java,
            delegate.aarMetadata,
            stats
        )
    }

    override val namespace: Provider<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                    VariantPropertiesMethodType.NAMESPACE_VALUE
            return delegate.namespace
        }

    override val aarMetadata: AarMetadata
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.VARIANT_AAR_METADATA_VALUE
            return userVisibleAarMetadata
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

    override val pseudoLocalesEnabled: Property<Boolean>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.VARIANT_PSEUDOLOCALES_ENABLED_VALUE
            return delegate.pseudoLocalesEnabled
        }
}
