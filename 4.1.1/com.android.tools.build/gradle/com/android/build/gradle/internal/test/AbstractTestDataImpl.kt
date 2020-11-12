/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.build.gradle.internal.test

import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.internal.component.TestCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.testing.StaticTestData
import com.android.build.gradle.internal.testing.TestData
import com.android.builder.testing.api.DeviceConfigProvider
import com.android.sdklib.AndroidVersion
import com.android.utils.ILogger
import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import java.io.File

/**
 * Common implementation of [TestData] for embedded test projects (in androidTest folder)
 * and separate module test projects.
 */
abstract class AbstractTestDataImpl(
    private val creationConfig: TestCreationConfig,
    private val variantSources: VariantSources,
    val testApkDir: Provider<Directory>,
    val testedApksDir: FileCollection?
) : TestData {
    private var extraInstrumentationTestRunnerArgs: Map<String, String> = mutableMapOf()

    override val applicationId: Provider<String>
        get() = creationConfig.applicationId

    override val testedApplicationId: Provider<String>
        get() = creationConfig.testedApplicationId

    override val instrumentationRunner: Provider<String>
        get() = creationConfig.instrumentationRunner

    override val instrumentationRunnerArguments: Map<String, String>
        get() {
            return ImmutableMap.builder<String, String>()
                .putAll(creationConfig.instrumentationRunnerArguments)
                .putAll(extraInstrumentationTestRunnerArgs)
                .build()
        }

    fun setExtraInstrumentationTestRunnerArgs(
        extraInstrumentationTestRunnerArgs: Map<String, String>
    ) {
        this.extraInstrumentationTestRunnerArgs =
            ImmutableMap.copyOf(
                extraInstrumentationTestRunnerArgs
            )
    }

    override var animationsDisabled: Boolean = false

    override val isTestCoverageEnabled: Boolean
        get() = creationConfig.isTestCoverageEnabled

    override val minSdkVersion: AndroidVersion
        get() = creationConfig.minSdkVersion

    override val flavorName: String
        get() = creationConfig.name

    open fun getTestedApksFromBundle(): FileCollection? = null

    override val testDirectories: List<File>
        get() {
            // For now we check if there are any test sources. We could inspect the test classes and
            // apply JUnit logic to see if there's something to run, but that would not catch the case
            // where user makes a typo in a test name or forgets to inherit from a JUnit class
            val javaDirectories =
                ImmutableList.builder<File>()
            for (sourceProvider in variantSources.sortedSourceProviders) {
                javaDirectories.addAll(sourceProvider.javaDirectories)
            }
            return javaDirectories.build()
        }

    override val testApk: File
        get() {
            val testApkOutputs = BuiltArtifactsLoaderImpl().load(testApkDir.get())
                ?: throw RuntimeException("No test APK in provided directory, file a bug")
            if (testApkOutputs.elements.size != 1) {
                throw RuntimeException(
                    "Unexpected number of main APKs, expected 1, got  "
                            + testApkOutputs.elements.size
                            + ":"
                            + Joiner.on(",").join(testApkOutputs.elements)
                )
            }
            return File(testApkOutputs.elements.iterator().next().outputFile)
        }

    abstract override fun getTestedApks(
        deviceConfigProvider: DeviceConfigProvider,
        logger: ILogger): List<File>

    override fun get(): StaticTestData {
        return StaticTestData(
            applicationId.get(),
            testedApplicationId.orNull,
            instrumentationRunner.get(),
            instrumentationRunnerArguments,
            animationsDisabled,
            isTestCoverageEnabled,
            minSdkVersion,
            isLibrary,
            flavorName,
            testApk,
            testDirectories,
            this::getTestedApks
        )
    }
}