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

import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.builder.testing.api.DeviceConfigProvider
import com.android.utils.ILogger
import com.google.common.base.Joiner
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File

/**
 * Data representing the test app and the tested application/library. Used as task input.
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
    @get:Optional
    val testedApplicationId: Provider<String>

    @get:Input
    val instrumentationRunner: Provider<String>

    @get:Input
    val instrumentationRunnerArguments: Map<String, String>

    @get:Input
    val animationsDisabled: Provider<Boolean>

    /** Returns whether the tested app is enabled for code coverage  */
    @get:Input
    val testCoverageEnabled: Provider<Boolean>

    /** The min SDK version of the app  */
    @get:Nested
    val minSdkVersion: Provider<AndroidVersion>

    /** If this is a library type. */
    @get:Input
    val libraryType: Provider<Boolean>

    /**
     * Returns the flavor name being test.
     *
     * @return the tested flavor name.
     */
    @get:Input
    val flavorName: Provider<String>

    /** Directory containing test APK. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val testApkDir: Provider<Directory>

    /**
     * Returns the APK containing the test classes for the application.
     *
     * @return the APK file.
     */
    @Internal("Already captured by testApkDir property.")
    fun getTestApk(): Provider<File> = testApkDir.map {
        val testApkOutputs = BuiltArtifactsLoaderImpl().load(it)
            ?: throw RuntimeException("No test APK in provided directory, file a bug")
        if (testApkOutputs.elements.size != 1) {
            throw RuntimeException(
                "Unexpected number of main APKs, expected 1, got  "
                        + testApkOutputs.elements.size
                        + ":"
                        + Joiner.on(",").join(testApkOutputs.elements)
            )
        }
        File(testApkOutputs.elements.iterator().next().outputFile)
    }

    /**
     * Returns the list of directories containing test so the build system can check the presence of
     * tests before deploying anything.
     *
     * @return list of folders containing test source files.
     */
    @get:Internal
    val testDirectories: ConfigurableFileCollection

    /**
     * Returns true if there are at least one test classes in the test APK. Non test related
     * classes such as R, BuildConfig and AndroidManifest classes are excluded.
     * This input is used to check the presence of tests before deploying anything.
     */
    @get:Internal
    val hasTests: Provider<Boolean>

    /**
     * Resolves all providers and returns a static version of this class
     *
     * @return [StaticTestData] version of this class
     */
    @Internal
    fun getAsStaticData(): StaticTestData

    /**
     * Returns an APK file to install based on given density and abis.
     *
     * @param deviceConfigProvider provider for the test device characteristics.
     * @return the file to install or null if non is compatible.
     */
    fun findTestedApks(deviceConfigProvider: DeviceConfigProvider, logger: ILogger): List<File>
}
