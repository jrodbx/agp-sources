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
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.internal.component.InstrumentedTestCreationConfig
import com.android.build.gradle.internal.tasks.databinding.DATA_BINDING_TRIGGER_CLASS
import com.android.build.gradle.internal.tasks.extractApkFilesBypassingBundleTool
import com.android.build.gradle.internal.test.BuiltArtifactsSplitOutputMatcher.computeBestOutput
import com.android.build.gradle.internal.testing.StaticTestData
import com.android.build.gradle.internal.testing.TestData
import com.android.builder.testing.api.DeviceConfigProvider
import com.android.ide.common.util.toPathString
import com.google.common.collect.ImmutableList
import com.google.common.io.Files
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * Common implementation of [TestData] for embedded test projects (in androidTest folder)
 * and separate module test projects.
 */
abstract class AbstractTestDataImpl(
    @get:Input
    val namespace: Provider<String>,
    creationConfig: InstrumentedTestCreationConfig,
    override val testApkDir: Provider<Directory>,
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    val testedApksDir: Provider<Directory>?,
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    val privacySandboxSdkApks: FileCollection?,
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    val privacySandboxCompatSdkApks: Provider<Directory>?,
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    val additionalSdkSupportedSplitApks: Provider<Directory>?,
    extraInstrumentationTestRunnerArgs: Map<String, String>
) : TestData {

    @get:Internal
    val privacyInstallBundlesFinder: ApkBundlesFinder
        get() = _privacyInstallBundlesFinder ?:
            object: ApkBundlesFinder {
                val privacySandboxApks: Set<File>? = privacySandboxSdkApks?.files

                override fun findBundles(
                    deviceConfigProvider: DeviceConfigProvider
                ): List<List<Path>> {
                    privacySandboxApks ?: return emptyList()
                    val privacySandboxInstallBundles = ImmutableList.builder<List<Path>>()
                    privacySandboxApks.forEach {  apk ->
                        privacySandboxInstallBundles.add(
                            extractApkFilesBypassingBundleTool(apk.toPath()))
                    }
                    return privacySandboxInstallBundles.build()
                }
            }.also { _privacyInstallBundlesFinder = it }

    private var _privacyInstallBundlesFinder: ApkBundlesFinder? = null

    @get:Input
    abstract val supportedAbis: Set<String>

    @get:Internal
    open val testedApksFinder: ApksFinder
        get() = _testedApksFinder ?:
            TestedApksFinder(
                testedApksDir?.let { BuiltArtifactsLoaderImpl().load(it) },
                privacySandboxCompatSdkApks?.let {
                    if (it.isPresent) {
                        BuiltArtifactsLoaderImpl().load(it)
                    } else {
                        null
                    }
                },
                additionalSdkSupportedSplitApks?.let {
                    if (it.isPresent) {
                        BuiltArtifactsLoaderImpl().load(it)
                    } else {
                        null
                    }
                },
                supportedAbis
            ).also {
                _testedApksFinder = it
            }

    private var _testedApksFinder: ApksFinder? = null

    internal class TestedApksFinder(
        private val testedApkBuiltArtifacts: BuiltArtifactsImpl?,
        private val privacySandboxCompatSdkApksBuiltArtifacts: BuiltArtifactsImpl?,
        private val additionalSdkSupportApkSplitsBuiltArtifacts: BuiltArtifactsImpl?,
        private val supportedAbis: Set<String>
    ) : ApksFinder {

        override fun findApks(deviceConfigProvider: DeviceConfigProvider): List<File> {
            testedApkBuiltArtifacts ?: return emptyList()
            val apks = mutableListOf<File>()
            apks += computeBestOutput(deviceConfigProvider, testedApkBuiltArtifacts, supportedAbis)
            // Add additional splits
            if (deviceConfigProvider.supportsPrivacySandbox) {
                additionalSdkSupportApkSplitsBuiltArtifacts?.let {
                    apks += it.elements.map { File(it.outputFile) }
                }
            } else {
                privacySandboxCompatSdkApksBuiltArtifacts?.let {
                    apks += it.elements.map { File(it.outputFile) }
                }
            }
            return apks
        }
    }

    override val applicationId = creationConfig.applicationId

    // Note: creationConfig.testedApplicationId returns the instrumentation target application ID.
    // testedApplicationId and instrumentationTargetPackageID are usually the same value
    // except for the one case where there are test.apk and app.apk and the self-instrumenting
    // flag is enabled. See TestApplicationTestData class.
    override val testedApplicationId = creationConfig.testedApplicationId

    override val instrumentationTargetPackageId = creationConfig.testedApplicationId

    override val instrumentationRunner = creationConfig.instrumentationRunner

    final override val instrumentationRunnerArguments =
        creationConfig.services.mapProperty(String::class.java, String::class.java)

    override var animationsDisabled = creationConfig.services.provider { false }

    override val testCoverageEnabled =
        creationConfig.services.provider { creationConfig.codeCoverageEnabled }

    override val minSdkVersion = creationConfig.services.provider { creationConfig.minSdk }

    override val flavorName = creationConfig.services.provider { creationConfig.flavorName ?: "" }

    override val testDirectories: ConfigurableFileCollection =
        creationConfig.services.fileCollection().also { fileCollection ->
            // For now we check if there are any test sources. We could inspect the test classes and
            // apply JUnit logic to see if there's something to run, but that would not catch the case
            // where user makes a typo in a test name or forgets to inherit from a JUnit class
            creationConfig.sources.java { javaSources -> fileCollection.from(javaSources.all) }
            creationConfig.sources.kotlin { kotlinSources -> fileCollection.from(kotlinSources.all) }
        }

    init {
        // lazily set the instrumentationRunnerArguments
        instrumentationRunnerArguments.set(creationConfig.instrumentationRunnerArguments)
        instrumentationRunnerArguments.putAll(extraInstrumentationTestRunnerArgs)
        // memoize the value which makes it similar to `by lazy`
        instrumentationRunnerArguments.finalizeValueOnRead()
    }

    override fun getAsStaticData(): StaticTestData {
        return StaticTestData(
                applicationId.get(),
                testedApplicationId.orNull,
                instrumentationTargetPackageId.get(),
                instrumentationRunner.get(),
                instrumentationRunnerArguments.get(),
                animationsDisabled.get(),
                testCoverageEnabled.get(),
                minSdkVersion.get(),
                libraryType.get(),
                flavorName.get(),
                getTestApk().get(),
                testDirectories.files.toList(),
                testedApksFinder,
                privacyInstallBundlesFinder
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

    override fun findTestedApks(deviceConfigProvider: DeviceConfigProvider): List<File> =
        testedApksFinder.findApks(deviceConfigProvider)

    override fun privacySandboxInstallBundlesFinder(
        deviceConfigProvider: DeviceConfigProvider): List<List<Path>> =
        privacyInstallBundlesFinder.findBundles(deviceConfigProvider)
}
