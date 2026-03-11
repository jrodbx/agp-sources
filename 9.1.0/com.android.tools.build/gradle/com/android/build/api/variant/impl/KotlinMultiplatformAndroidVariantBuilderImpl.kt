/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.build.api.variant.impl

import com.android.build.api.component.analytics.AnalyticsEnabledKotlinMultiplatformAndroidVariantBuilder
import com.android.build.api.variant.AndroidTestBuilder
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.DeviceTestBuilder
import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.KotlinMultiplatformAndroidVariantBuilder
import com.android.build.api.variant.TestSuiteBuilder
import com.android.build.api.variant.VariantBuilder
import com.android.build.gradle.internal.core.dsl.KmpVariantDslInfo
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.internal.profile.ProfilingMode
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.VariantBuilderServices
import com.android.build.gradle.internal.testsuites.impl.TestSuiteBuilderImpl
import com.android.build.gradle.options.StringOption
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import javax.inject.Inject

open class KotlinMultiplatformAndroidVariantBuilderImpl
@Inject
constructor(
  globalVariantBuilderConfig: GlobalVariantBuilderConfig,
  dslInfo: KmpVariantDslInfo,
  componentIdentity: ComponentIdentity,
  variantBuilderServices: VariantBuilderServices,
) :
  VariantBuilderImpl(globalVariantBuilderConfig, dslInfo, componentIdentity, variantBuilderServices),
  KotlinMultiplatformAndroidVariantBuilder,
  InternalVariantBuilder {

  override var androidTestEnabled: Boolean
    get() = androidTest.enable
    set(value) {
      androidTest.enable = value
    }

  override var enableAndroidTest: Boolean
    get() = androidTest.enable
    set(value) {
      androidTest.enable = value
    }

  override var enableTestFixtures: Boolean = dslInfo.testFixtures?.enable ?: false

  override fun <T : VariantBuilder> createUserVisibleVariantObject(
    projectServices: ProjectServices,
    stats: GradleBuildVariant.Builder?,
  ): T =
    if (stats == null) {
      this as T
    } else {
      projectServices.objectFactory.newInstance(AnalyticsEnabledKotlinMultiplatformAndroidVariantBuilder::class.java, this, stats) as T
    }

  override var targetSdk: Int?
    get() = super.targetSdk
    set(value) {
      variantBuilderServices.deprecationReporter.reportObsoleteUsage(
        "libraryVariant.targetSdk",
        DeprecationReporter.DeprecationTarget.VERSION_9_0,
      )
      super.targetSdk = value
    }

  override var targetSdkPreview: String?
    get() = super.targetSdkPreview
    set(value) {
      variantBuilderServices.deprecationReporter.reportObsoleteUsage(
        "libraryVariant.targetSdkPreview",
        DeprecationReporter.DeprecationTarget.VERSION_9_0,
      )
      super.targetSdkPreview = value
    }

  override var isMinifyEnabled: Boolean = dslInfo.optimizationDslInfo.postProcessingOptions.codeShrinkerEnabled()

  override val deviceTests: Map<String, DeviceTestBuilderImpl> =
    DeviceTestBuilderImpl.create(
      dslInfo.dslDefinedDeviceTests,
      variantBuilderServices,
      globalVariantBuilderConfig,
      { targetSdkVersion },
      dslInfo.androidTestMultiDexEnabled,
      ProfilingMode.getProfilingModeType(variantBuilderServices.projectOptions[StringOption.PROFILING_MODE]).isDebuggable == true,
    )

  override val androidTest: AndroidTestBuilder by
    lazy(LazyThreadSafetyMode.NONE) {
      AndroidTestBuilderImpl(
        deviceTests.get(DeviceTestBuilder.ANDROID_TEST_TYPE) ?: throw RuntimeException("No androidTest component defined on this variant")
      )
    }

  override val hostTests: Map<String, HostTestBuilder> =
    HostTestBuilderImpl.create(dslInfo.dslDefinedHostTests, dslInfo.experimentalProperties)

  override val suites: Map<String, TestSuiteBuilder> =
    TestSuiteBuilderImpl.create(dslInfo.dslDefinedTestSuites, variantBuilderServices, dslInfo.experimentalProperties)
}
