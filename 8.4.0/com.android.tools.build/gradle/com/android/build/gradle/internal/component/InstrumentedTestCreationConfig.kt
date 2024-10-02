/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.component

import org.gradle.api.provider.Provider

interface InstrumentedTestCreationConfig: ApkCreationConfig, TestCreationConfig {

    /**
     * Returns the instrumentationRunner to use to test this variant, or if the variant is a test,
     * the one to use to test the tested variant.
     *
     * @return the instrumentation test runner name
     */
    override val instrumentationRunner: Provider<String>

    /**
     * Returns the instrumentation runner arguments to use to test this variant, or if the variant is
     * a test, the ones to use to test the tested variant
     *
     * @return the instrumentation runner arguments mapping
     */
    val instrumentationRunnerArguments: Provider<Map<String, String>>

    /**
     * Returns handleProfiling value to use to test this variant, or if the variant is a test, the
     * one to use to test the tested variant.
     *
     * @return the handleProfiling value
     */
    val handleProfiling: Provider<Boolean>

    /**
     * Returns functionalTest value to use to test this variant, or if the variant is a test, the
     * one to use to test the tested variant.
     *
     * @return the functionalTest value
     */
    val functionalTest: Provider<Boolean>

    /** Gets the test label for this variant  */
    val testLabel: Provider<String?>
}
