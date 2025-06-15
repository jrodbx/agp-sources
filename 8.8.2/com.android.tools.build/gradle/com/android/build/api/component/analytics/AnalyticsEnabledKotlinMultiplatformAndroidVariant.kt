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
import com.android.build.api.variant.AndroidTest
import com.android.build.api.variant.Component
import com.android.build.api.variant.DeviceTest
import com.android.build.api.variant.HostTest
import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.Instrumentation
import com.android.build.api.variant.KotlinMultiplatformAndroidVariant
import com.android.build.api.variant.LifecycleTasks
import com.android.build.api.variant.Sources
import com.android.build.api.variant.TestedComponentPackaging
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class AnalyticsEnabledKotlinMultiplatformAndroidVariant @Inject constructor(
    private val delegate: KotlinMultiplatformAndroidVariant,
    private val stats: GradleBuildVariant.Builder,
    private val objectFactory: ObjectFactory
): KotlinMultiplatformAndroidVariant {

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

    override val lifecycleTasks: LifecycleTasks
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.LIFECYCLE_TASKS_VALUE
            return delegate.lifecycleTasks
        }

    override val unitTest: com.android.build.api.component.UnitTest?
        get() = hostTests[HostTestBuilder.UNIT_TEST_TYPE]
                as? com.android.build.api.component.UnitTest


    private val userVisibleAndroidTest: AnalyticsEnabledAndroidTest? by lazy(LazyThreadSafetyMode.SYNCHRONIZED){
        delegate.androidTest?.let {
            objectFactory.newInstance(
                AnalyticsEnabledAndroidTest::class.java,
                it,
                stats
            )
        }
    }

    @Suppress("DEPRECATION")
    override val androidTest: com.android.build.api.variant.AndroidTest?
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.ANDROID_TEST_VALUE
            return userVisibleAndroidTest
        }

    override val nestedComponents: List<Component>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.NESTED_COMPONENTS_VALUE
            return delegate.nestedComponents
        }

    override val deviceTests: Map<String, DeviceTest>
        get()  {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.DEVICE_TESTS_VALUE
            // return a new list everytime as items may eventually be added through future APIs.
            // we may consider returning a live map instead.
            return  delegate.deviceTests.mapValues {
                val value = it.value
                if (value is AndroidTest) {
                    AnalyticsEnabledAndroidTest(value, stats, objectFactory)
                } else {
                    AnalyticsEnabledDeviceTest(value, stats, objectFactory)
                }
            }
        }

    override val hostTests: Map<String, HostTest>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.HOST_TESTS_VALUE
            // return a new list everytime as items may eventually be added through future APIs.
            // we may consider returning a live map instead.
            return  delegate.hostTests.mapValues {
                AnalyticsEnabledHostTest(it.value, stats, objectFactory)
            }
        }

    private val userVisiblePackaging: TestedComponentPackaging by lazy(LazyThreadSafetyMode.SYNCHRONIZED){
        objectFactory.newInstance(
            AnalyticsEnabledTestedComponentPackaging::class.java,
            delegate.packaging,
            stats
        )
    }

    override val packaging: TestedComponentPackaging
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.PACKAGING_OPTIONS_VALUE
            return userVisiblePackaging
        }
}
