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

package com.android.build.gradle.internal.component

import com.android.build.api.variant.AndroidVersion
import org.gradle.api.Named
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider

/**
 * Interface for properties common to all test components.
 */
interface TestCreationConfig: ComponentCreationConfig, Named {

    /**
     * The application of the app under tests
     */
    val testedApplicationId: Provider<String>

    /**
     * Returns the instrumentationRunner to use to test this variant, or if the variant is a test,
     * the one to use to test the tested variant.
     *
     * @return the instrumentation test runner name
     */
    val instrumentationRunner: Provider<String>

    /**
     * Returns the instrumentationRunner arguments to use to test this variant, or if the variant is
     * a test, the ones to use to test the tested variant
     */
    val instrumentationRunnerArguments: Map<String, String>

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

    val isTestCoverageEnabled: Boolean

    val manifestPlaceholders: MapProperty<String, String>

    val targetSdkVersion: AndroidVersion

}
