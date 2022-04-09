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

package com.android.build.api.instrumentation

import com.android.build.api.dsl.Device
import java.io.File
import org.gradle.api.Incubating
import org.gradle.api.logging.Logger

/**
 * An interface for a Gradle Managed Device test runner.
 *
 * If you work on creating a new device type of Gradle Managed Device,
 * you have to provide an implementation class of this interface and
 * construct it by your Managed Device DSL implementation class that
 * implements [ManagedDeviceTestRunnerFactory].
 *
 * @suppress Do not use from production code. All properties in this interface are exposed for
 * prototype.
 */
@Incubating
interface ManagedDeviceTestRunner {

    /**
     * Runs an Android instrumentation test on a given device.
     *
     * @return true if and only if all test cases are passed. Otherwise, false.
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @Incubating
    fun runTests(
        managedDevice: Device,
        runId: String,
        outputDirectory: File,
        coverageOutputDirectory: File,
        additionalTestOutputDir: File?,
        projectPath: String,
        variantName: String,
        testData: StaticTestData,
        additionalInstallOptions: List<String>,
        helperApks: Set<File>,
        logger: Logger
    ): Boolean
}
