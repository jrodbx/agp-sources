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

package com.android.build.api.dsl

import org.gradle.api.Incubating

/**
 * List of default properties that can be used to initialize the [AgpTestSuite.testEngineInputs]
 * for a particular test suite.
 *
 * Can be used as :
 * ```
 * android {
 *     testOptions {
 *         suites {
 *             create("myHostTestSuite") {
 *                 useJunitEngine() {
 *                    DefaultInputsForAgpTestSuites.HOST_TEST.initialize(this)
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 */
/** @suppress */
enum class DefaultInputsForAgpTestSuites(
    /**
     * List of properties that will be made available to the junit test engine by the test task.
     *
     * At this time, the lists definition are mostly for testing purposes.
     *
     * TODO: change comment once property handover is implemented.
     * These properties are guaranteed to be provided by the time the test start and a
     * retrieval mechanism will be added in a subsequent CL.
     */
    val supportedProperties: List<AgpTestSuiteInputParameters>
) {
    @Incubating
    HOST_TEST(
        listOf(
            AgpTestSuiteInputParameters.TEST_CLASSES,
        )
    ),
    @Incubating
    DEVICE_TEST(
        listOf(
            AgpTestSuiteInputParameters.MERGED_MANIFEST,
            AgpTestSuiteInputParameters.TESTED_APKS,
            AgpTestSuiteInputParameters.TESTING_APK,
        )
    ),
    @Incubating
    JOURNEYS_TEST(
        listOf(
            AgpTestSuiteInputParameters.MERGED_MANIFEST,
        ).plus(HOST_TEST.supportedProperties)
    );

    @Incubating
    fun initialize(testSuite: JUnitEngineSpec) {
        testSuite.inputs.addAll(supportedProperties)
    }
}
