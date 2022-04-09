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

import com.android.build.api.variant.AarMetadata
import com.android.build.api.variant.AndroidTest
import com.android.build.api.variant.LibraryVariant
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.TestFixtures
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class AnalyticsEnabledLibraryVariant @Inject constructor(
    override val delegate: LibraryVariant,
    stats: GradleBuildVariant.Builder,
    objectFactory: ObjectFactory
) : AnalyticsEnabledVariant(
    delegate, stats, objectFactory
), LibraryVariant {

    override val androidTest: AndroidTest?
        get() = delegate.androidTest

    private val userVisibleTestFixtures: TestFixtures? by lazy {
        delegate.testFixtures?.let {
            objectFactory.newInstance(
                AnalyticsEnabledTestFixtures::class.java,
                it,
                stats
            )
        }
    }

    override val testFixtures: TestFixtures?
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.TEST_FIXTURES_VALUE
            return userVisibleTestFixtures
        }

    private val userVisibleRenderscript: Renderscript by lazy {
        objectFactory.newInstance(
            AnalyticsEnabledRenderscript::class.java,
            delegate.renderscript,
            stats
        )
    }

    override val renderscript: Renderscript?
        get() {
            return if (delegate.renderscript != null) {
                stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                    VariantPropertiesMethodType.RENDERSCRIPT_VALUE
                userVisibleRenderscript
            } else null
        }

    private val userVisibleAarMetadata: AarMetadata by lazy {
        objectFactory.newInstance(
            AnalyticsEnabledAarMetadata::class.java,
            delegate.aarMetadata,
            stats
        )
    }
    override val aarMetadata: AarMetadata
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.VARIANT_AAR_METADATA_VALUE
            return userVisibleAarMetadata
        }
}
