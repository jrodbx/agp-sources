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

package com.android.builder.model.v2.ide

import com.android.builder.model.v2.AndroidModel
import com.android.builder.model.v2.models.AssetsTestSuiteSource
import com.android.builder.model.v2.models.HostJarTestSuiteSource
import com.android.builder.model.v2.models.SourceType
import com.android.builder.model.v2.models.TestApkTestSuiteSource

interface TestSuite : AndroidModel {

  /** Name of the test suite. */
  val name: String

  /** Configured junit engines for this test suite. */
  val junitEngineInfo: JUnitEngineInfo

  /** Generated [SourceType.ASSETS] source folder(s) for this test suite. */
  val generatedAssets: Collection<AssetsTestSuiteSource>

  /** Generated [SourceType.HOST_JAR] source folders for this test suite. */
  val generatedHostJars: Collection<HostJarTestSuiteSource>

  /** Generated [SourceType.TEST_APK] sources folder for this test suite. */
  val generatedTestApks: Collection<TestApkTestSuiteSource>
}
