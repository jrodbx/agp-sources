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

import com.android.build.api.variant.LibraryPackagingOptions
import com.android.build.api.variant.LibraryVariant
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import javax.inject.Inject

open class AnalyticsEnabledLibraryVariant @Inject constructor(
    override val delegate: LibraryVariant,
    stats: GradleBuildVariant.Builder,
    objectFactory: ObjectFactory
) : AnalyticsEnabledVariant(
    delegate, stats, objectFactory
), LibraryVariant {
    override val applicationId: Provider<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.READ_ONLY_APPLICATION_ID_VALUE
            return delegate.applicationId
        }

    private val userVisiblePackagingOptions: LibraryPackagingOptions by lazy {
        objectFactory.newInstance(
            AnalyticsEnabledLibraryPackagingOptions::class.java,
            delegate.packagingOptions,
            stats
        )
    }

    override val packagingOptions: LibraryPackagingOptions
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.PACKAGING_OPTIONS_VALUE
            return userVisiblePackagingOptions
        }

    override fun packagingOptions(action: LibraryPackagingOptions.() -> Unit) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.PACKAGING_OPTIONS_ACTION_VALUE
        action.invoke(userVisiblePackagingOptions)
    }
}
