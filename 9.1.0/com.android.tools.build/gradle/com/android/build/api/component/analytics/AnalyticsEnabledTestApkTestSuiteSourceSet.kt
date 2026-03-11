/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.build.api.variant.SourceDirectories
import com.android.build.api.variant.TestSuiteSourceSet
import com.android.build.api.variant.TestSuiteSourceType
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import java.io.File

open class AnalyticsEnabledTestApkTestSuiteSourceSet(
  private val source: TestSuiteSourceSet.TestApk,
  private val stats: GradleBuildVariant.Builder,
) : AnalyticsEnabledTestSuiteSourceSet(source, stats), TestSuiteSourceSet.TestApk {

  override val java: SourceDirectories.Flat?
    get() {
      stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type = VariantPropertiesMethodType.TEST_SUITE_SOURCE_JAVA_VALUE
      return source.java
    }

  override val kotlin: SourceDirectories.Flat?
    get() {
      stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type = VariantPropertiesMethodType.TEST_SUITE_SOURCE_KOTLIN_VALUE
      return source.kotlin
    }

  override val resources: SourceDirectories.Flat
    get() {
      stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type = VariantPropertiesMethodType.TEST_SUITE_SOURCE_RESOURCES_VALUE
      return source.resources
    }

  override val manifestFile: File
    get() {
      stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
        VariantPropertiesMethodType.TEST_SUITE_SOURCE_MANIFEST_FILE_VALUE
      return source.manifestFile
    }

  override val type: TestSuiteSourceType
    get() = TestSuiteSourceType.TEST_APK
}
