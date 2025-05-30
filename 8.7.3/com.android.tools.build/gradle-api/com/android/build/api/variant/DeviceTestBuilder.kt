/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.api.variant

import org.gradle.api.Incubating

/**
 * Interface to turn tests on devices, `AndroidTest` being an example of device tests.
 */
@Incubating
interface DeviceTestBuilder: GeneratesApkBuilder {
    companion object {

        /**
         * Host test type for default unit tests.
         */
        @Incubating
        const val ANDROID_TEST_TYPE = "AndroidTest"
    }

    /**
     * Set to `true` if the variant's has any device tests, false otherwise.
     * Value is [Boolean#True] by default.
     */
    @get: Incubating
    @set: Incubating
    var enable: Boolean



    /**
     * Specifies host test code coverage data collection by configuring the JacocoPlugin.
     *
     * When enabled, the Jacoco plugin is applied and coverage data is collected
     * by the Jacoco plugin. This can avoid unwanted build time instrumentation required to collect
     * coverage data from other test types such as unit tests.
     *
     * If the value is initialized from the DSL [com.android.build.api.dsl.BuildType.enableAndroidTestCoverage],
     */
    @get:Incubating
    @get:Deprecated(
        message="Other plugins can change this value, it is not safe to read it at this stage, " +
                "use [HostTest.enableCodeCoverage]",
        level = DeprecationLevel.ERROR
    )
    @set: Incubating
    var enableCodeCoverage: Boolean
}
