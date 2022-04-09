/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.api.artifact.Artifacts
import com.android.build.api.variant.AarMetadata
import com.android.build.api.variant.AndroidTest
import com.android.build.api.variant.Instrumentation
import com.android.build.api.variant.Sources
import com.android.build.api.variant.impl.KotlinMultiplatformAndroidVariant
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import javax.inject.Inject

open class AnalyticsEnabledKotlinMultiplatformAndroidVariant @Inject constructor(
    private val delegate: KotlinMultiplatformAndroidVariant,
    private val stats: GradleBuildVariant.Builder,
    private val objectFactory: ObjectFactory
): KotlinMultiplatformAndroidVariant {

    override val namespace: Provider<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.NAMESPACE_VALUE
            return delegate.namespace
        }
    override val name: String
        get() = delegate.name
    override val artifacts: Artifacts
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.ARTIFACTS_VALUE
            return objectFactory.newInstance(
                AnalyticsEnabledArtifacts::class.java,
                delegate.artifacts,
                stats,
                objectFactory)
        }
    override val sources: Sources
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.COMPONENT_SOURCES_ACCESS_VALUE
            return objectFactory.newInstance(
                AnalyticsEnabledSources::class.java,
                delegate.sources,
                stats,
                objectFactory)
        }
    override val instrumentation: Instrumentation
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.INSTRUMENTATION_VALUE
            return objectFactory.newInstance(
                AnalyticsEnabledInstrumentation::class.java,
                delegate.instrumentation,
                stats,
                objectFactory
            )
        }

    override val compileClasspath: FileCollection
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.COMPILE_CLASSPATH_VALUE
            return delegate.compileClasspath
        }
    override val compileConfiguration: Configuration
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.COMPILE_CONFIGURATION_VALUE
            return delegate.compileConfiguration
        }

    override val runtimeConfiguration: Configuration
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.RUNTIME_CONFIGURATION_VALUE
            return delegate.runtimeConfiguration
        }
    private val userVisibleUnitTest: AnalyticsEnabledUnitTest? by lazy {
        delegate.unitTest?.let {
            objectFactory.newInstance(
                AnalyticsEnabledUnitTest::class.java,
                it,
                stats
            )
        }
    }

    override val unitTest: com.android.build.api.component.UnitTest?
        get() = userVisibleUnitTest
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
    private val userVisibleAndroidTest: AnalyticsEnabledAndroidTest? by lazy {
        delegate.androidTest?.let {
            objectFactory.newInstance(
                AnalyticsEnabledAndroidTest::class.java,
                it,
                stats
            )
        }
    }

    override val androidTest: AndroidTest?
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.ANDROID_TEST_VALUE
            return userVisibleAndroidTest
        }
}
