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

import com.android.SdkConstants
import com.android.build.gradle.internal.component.InstrumentedTestCreationConfig
import com.android.build.gradle.internal.component.TestCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.tasks.databinding.DATA_BINDING_TRIGGER_CLASS
import com.android.build.gradle.internal.testing.StaticTestData
import com.android.build.gradle.internal.testing.TestData
import com.android.ide.common.util.toPathString
import com.google.common.collect.ImmutableMap
import com.google.common.io.Files
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File
import java.util.concurrent.Callable
import java.util.zip.ZipFile

/**
 * Common implementation of [TestData] for embedded test projects (in androidTest folder)
 * and separate module test projects.
 */
abstract class AbstractTestDataImpl(
        @get:Input
        val namespace: Provider<String>,
        creationConfig: TestCreationConfig,
        instrumentedTestCreationConfig: InstrumentedTestCreationConfig,
        variantSources: VariantSources,
        override val testApkDir: Provider<Directory>,
        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        @get:Optional
        val testedApksDir: FileCollection?
) : TestData {

    private var extraInstrumentationTestRunnerArgs: Map<String, String> = mutableMapOf()

    override val applicationId = creationConfig.applicationId

    // Note: creationConfig.testedApplicationId returns the instrumentation target application ID.
    // testedApplicationId and instrumentationTargetPackageID are usually the same value
    // except for the one case where there are test.apk and app.apk and the self-instrumeting
    // flag is enabled. See TestApplicationTestData class.
    override val testedApplicationId = creationConfig.testedApplicationId

    override val instrumentationTargetPackageId = creationConfig.testedApplicationId

    override val instrumentationRunner = instrumentedTestCreationConfig.instrumentationRunner

    override val instrumentationRunnerArguments: Map<String, String> by lazy {
        ImmutableMap.builder<String, String>()
            .putAll(instrumentedTestCreationConfig.instrumentationRunnerArguments)
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

    override var animationsDisabled = creationConfig.services.provider { false }

    override val testCoverageEnabled =
        creationConfig.services.provider { creationConfig.isTestCoverageEnabled }

    override val minSdkVersion = creationConfig.services.provider { creationConfig.minSdkVersion }

    override val flavorName = creationConfig.services.provider { creationConfig.flavorName ?: "" }

    override val testDirectories: ConfigurableFileCollection =
        creationConfig.services.fileCollection().from(Callable<List<File>> {
            // For now we check if there are any test sources. We could inspect the test classes and
            // apply JUnit logic to see if there's something to run, but that would not catch the case
            // where user makes a typo in a test name or forgets to inherit from a JUnit class
            variantSources.sortedSourceProviders.flatMap { it.javaDirectories }
        })

    override fun getAsStaticData(): StaticTestData {
        return StaticTestData(
                applicationId.get(),
                testedApplicationId.orNull,
                instrumentationTargetPackageId.get(),
                instrumentationRunner.get(),
                instrumentationRunnerArguments,
                animationsDisabled.get(),
                testCoverageEnabled.get(),
                minSdkVersion.get(),
                libraryType.get(),
                flavorName.get(),
                getTestApk().get(),
                testDirectories.files.toList(),
                this::findTestedApks
        )
    }

    override fun hasTests(
        allClasses: FileCollection,
        rClasses: FileCollection,
        buildConfig: FileCollection
    ): Provider<Boolean> =
        allClasses
            .minus(rClasses)
            .minus(buildConfig).elements.map { testClasses ->
                val namespaceDir = namespace.get().replace('.', '/')
                val DATA_BINDER_MAPPER_IMPL = "DataBinderMapperImpl"
                val ignoredPaths = setOf(
                    "${namespaceDir}/${SdkConstants.FN_BUILD_CONFIG_BASE}${SdkConstants.DOT_CLASS}",
                    "${namespaceDir}/${SdkConstants.FN_MANIFEST_BASE}${SdkConstants.DOT_CLASS}",
                    "${namespaceDir}/${DATA_BINDING_TRIGGER_CLASS}${SdkConstants.DOT_CLASS}",
                    "${namespaceDir}/$DATA_BINDER_MAPPER_IMPL${SdkConstants.DOT_CLASS}",
                )
                val regexIgnoredPaths = setOf(
                    "androidx/databinding/.*\\${SdkConstants.DOT_CLASS}".toRegex(), // Classes in androidx/databinding
                    "${namespaceDir}/$DATA_BINDER_MAPPER_IMPL\\\$.*\\${SdkConstants.DOT_CLASS}".toRegex(), // DataBinderMapplerImpl inner classes
                    ".*/BR${SdkConstants.DOT_CLASS}".toRegex(), // BR.class files
                )
                val isNotIgnoredClass = { relativePath: String ->
                    Files.getFileExtension(relativePath)==SdkConstants.EXT_CLASS &&
                            relativePath !in ignoredPaths &&
                            !regexIgnoredPaths.any { it.matches(relativePath) }
                }

                for (fileSystemLocation in testClasses) {
                    val jarOrDirectory = fileSystemLocation.asFile
                    if (!jarOrDirectory.exists()) {
                        continue
                    }
                    if (jarOrDirectory.isDirectory) {
                        for (file in jarOrDirectory.walk()) {
                            if (isNotIgnoredClass(jarOrDirectory.toPath()
                                    .relativize(file.toPath())
                                    .toPathString().portablePath)) {
                                return@map true
                            }
                        }
                    } else {
                        ZipFile(jarOrDirectory).use {
                            for (entry in it.entries()) {
                                if (isNotIgnoredClass(entry.name)) {
                                    return@map true
                                }
                            }
                        }
                    }
                }
                false
            }
}
