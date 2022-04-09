/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.api.instrumentation.manageddevice

import java.io.File
import org.gradle.api.Incubating
import org.gradle.api.file.Directory

/**
 * The general inputs associated with a given test run generated from the build.
 *
 * @suppress Do not use from production code. All properties in this interface are exposed for
 * prototype.
 */
@Incubating
interface TestRunData {

    /**
     * The full name of the variant under test.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get: Incubating
    val variantName: String

    /**
     * A unique id for the test-run.
     *
     * This is based upon the managed device name, variant, and module that this task
     * belongs to.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get: Incubating
    val testRunId: String

    /**
     * The name of the managed device from the DSL associated with this test run.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get: Incubating
    val deviceName: String

    /**
     * The output directory to contain test results
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get: Incubating
    val outputDirectory: Directory

    /**
     * The output directory to contain code coverage results
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get: Incubating
    val coverageOutputDirectory: Directory

    /**
     * Output directory to contain any additional test results.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get: Incubating
    val additionalTestOutputDir: Directory?

    /**
     * Additional APK installation options.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get: Incubating
    val additionalInstallOptions: List<String>

    /**
     * Helper APKs for the given test run.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get: Incubating
    val helperApks: Set<File>

    /**
     * The path to the Gradle project for this test.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get: Incubating
    val projectPath: String

    /**
     * The test data associated with this test run, including the Test APK and APK under test.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get: Incubating
    val testData: StaticTestData

}
