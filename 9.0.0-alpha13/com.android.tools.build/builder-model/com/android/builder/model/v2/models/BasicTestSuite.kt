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

package com.android.builder.model.v2.models

import com.android.builder.model.v2.AndroidModel
import com.android.builder.model.v2.ide.TestSuiteVariantTarget

/**
 * Information about a test suite attached to the project.
 */
interface BasicTestSuite: AndroidModel {

    val name: String

    /**
     * [SourceType.ASSETS] source folder(s) for this test suite.
     */
    val assets: Collection<AssetsTestSuiteSource>

    /**
     * [SourceType.HOST_JAR] source folders for this test suite.
     */
    val hostJars: Collection<HostJarTestSuiteSource>

    /**
     * [SourceType.TEST_APK] sources folder for this test suite.
     */
    val testApks: Collection<TestApkTestSuiteSource>

    /**
     * Variant specific target(s) for this test suite.
     */
    val targetsByVariant: Collection<TestSuiteVariantTarget>
}
