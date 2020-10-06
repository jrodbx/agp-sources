/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.gradle.internal.testing

import com.android.builder.testing.api.DeviceConfigProvider
import com.android.ide.common.process.ProcessException
import com.android.sdklib.AndroidVersion
import com.android.utils.ILogger
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import java.io.File

/**
 * Data representing the test app and the tested application/library.
 */
interface TestData {
    /**
     * Returns the application id.
     *
     * @return the id
     */
    @get:Input
    val applicationId: Provider<String>

    /**
     * Returns the tested application id. This can be empty if the test package is self-contained.
     *
     * @return the id or null.
     */
    @get:Input
    val testedApplicationId: Provider<String>

    @get:Input
    val instrumentationRunner: Provider<String>

    @get:Input
    val instrumentationRunnerArguments: Map<String, String>

    @get:Input
    val animationsDisabled: Boolean

    /** Returns whether the tested app is enabled for code coverage  */
    @get:Input
    val isTestCoverageEnabled: Boolean

    /** The min SDK version of the app  */
    @get:Input
    val minSdkVersion: AndroidVersion
    val isLibrary: Boolean

    /**
     * Returns an APK file to install based on given density and abis.
     *
     * @param deviceConfigProvider provider for the test device characteristics.
     * @return the file to install or null if non is compatible.
     */
    @Throws(ProcessException::class)
    fun getTestedApks(
        deviceConfigProvider: DeviceConfigProvider,
        logger: ILogger
    ): List<File?>

    /**
     * Returns the flavor name being test.
     *
     * @return the tested flavor name.
     */
    @get:Input
    val flavorName: String

    /**
     * Returns the APK containing the test classes for the application.
     *
     * @return the APK file.
     */
    @get:Input
    val testApk: File

    /**
     * Returns the list of directories containing test so the build system can check the presence of
     * tests before deploying anything.
     *
     * @return list of folders containing test source files.
     */
    @get:Input
    val testDirectories: List<File?>

    /**
     * Resolves all providers and returns a static version of this class
     *
     * @return StaticTestData version of this class
     */
    fun get(): StaticTestData
}