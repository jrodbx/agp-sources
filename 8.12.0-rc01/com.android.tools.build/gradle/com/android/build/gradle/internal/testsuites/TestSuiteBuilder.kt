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

package com.android.build.gradle.internal.testsuites

import com.android.build.api.dsl.JUnitEngineSpec
import org.gradle.api.Named

/**
 * Interface to configure [TestSuite] during the [com.android.build.api.variant.AndroidComponentsExtension.beforeVariants]
 * callbacks.
 *
 * TODO : Provide example before moving to public interfaces
 */
/** @suppress */
interface TestSuiteBuilder: Named {

    /**
     * Enables or disable the test suite for the current variant.
     */
    var enable: Boolean

    /**
     * Configure the [JUnitEngineSpec] for this test suite in this variant.
     */
    val junitEngineSpec: JUnitEngineSpec

    /**
     * Configure the list of [TestSuiteTargetBuilder] for this test suite in this variant.
     *
     * The [Map] keys are the test suite names.
     */
    val targets: Map<String, TestSuiteTargetBuilder>
}
