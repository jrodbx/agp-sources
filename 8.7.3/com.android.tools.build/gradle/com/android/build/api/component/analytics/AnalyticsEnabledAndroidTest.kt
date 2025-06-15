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

import com.android.build.api.variant.AndroidTest
import com.android.build.api.variant.AndroidResources
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.GeneratesApk
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.ApkPackaging
import com.android.build.api.variant.Dexing
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.ResValue
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import java.io.Serializable
import javax.inject.Inject

open class AnalyticsEnabledAndroidTest @Inject constructor(
    override val delegate: AndroidTest,
    stats: GradleBuildVariant.Builder,
    objectFactory: ObjectFactory
) : AnalyticsEnabledDeviceTest(
    delegate, stats, objectFactory
), AndroidTest {

    override val instrumentationRunner: Property<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.INSTRUMENTATION_RUNNER_VALUE
            return delegate.instrumentationRunner
        }

    override val instrumentationRunnerArguments: MapProperty<String, String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.INSTRUMENTATION_RUNNER_ARGUMENTS_VALUE
            return delegate.instrumentationRunnerArguments
        }

    override val handleProfiling: Property<Boolean>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.HANDLE_PROFILING_VALUE
            return delegate.handleProfiling
        }
    override val functionalTest: Property<Boolean>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.FUNCTIONAL_TEST_VALUE
            return delegate.functionalTest
        }
    override val testLabel: Property<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.TEST_LABEL_VALUE
            return delegate.testLabel
        }

    override val buildConfigFields: MapProperty<String, out BuildConfigField<out Serializable>>
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

    override val dexing: Dexing
        get() = generatesApk.dexing

    private val generatesApk: GeneratesApk by lazy(LazyThreadSafetyMode.SYNCHRONIZED){
        AnalyticsEnabledGeneratesApk(
                delegate,
                stats,
                objectFactory
        )
    }

    override val androidResources: AndroidResources
        get() = generatesApk.androidResources

    override val renderscript: Renderscript?
        get() = generatesApk.renderscript

    override val packaging: ApkPackaging
        get() = generatesApk.packaging

    override val targetSdk: AndroidVersion
        get() = generatesApk.targetSdk

    override val targetSdkVersion: AndroidVersion
        get() = generatesApk.targetSdkVersion

    override val codeCoverageEnabled: Boolean
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.DEVICE_TEST_CODE_COVERAGE_ENABLED_VALUE
            return delegate.codeCoverageEnabled
        }
}
