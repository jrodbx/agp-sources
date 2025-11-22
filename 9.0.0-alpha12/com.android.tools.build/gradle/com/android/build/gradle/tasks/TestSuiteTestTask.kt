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

package com.android.build.gradle.tasks

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.AgpTestSuiteInputParameters
import com.android.build.api.testsuites.TestEngineInputProperty
import com.android.build.api.testsuites.TestSuiteExecutionClient.Companion.DEFAULT_ENV_VARIABLE
import com.android.build.api.variant.impl.JUnitEngineSpecImplForVariant
import com.android.build.api.variant.impl.TestSuiteSourceContainer
import com.android.build.gradle.internal.AvdComponentsBuildService
import com.android.build.gradle.internal.BuildToolsExecutableInput
import com.android.build.gradle.internal.api.TestSuiteSourceSet
import com.android.build.gradle.internal.component.TestSuiteCreationConfig
import com.android.build.gradle.internal.component.TestSuiteTargetCreationConfig
import com.android.build.gradle.internal.computeAvdName
import com.android.build.gradle.internal.dsl.ManagedVirtualDevice
import com.android.build.gradle.internal.initialize
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask.DeviceProviderFactory
import com.android.build.gradle.internal.tasks.GlobalTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.StringOption
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.testing.api.DeviceException
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import shadow.bundletool.com.android.utils.PathUtils
import java.io.File
import java.io.FileWriter
import java.util.Properties
import kotlin.io.path.Path

@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class TestSuiteTestTask: Test(), GlobalTask {

    @get:Nested
    abstract val engineInputParameters: ListProperty<AgpTestSuiteInputParameter>

    @get:Nested
    abstract val buildTools: BuildToolsExecutableInput

    @get:Input
    abstract val engineInputProperties: MapProperty<String, String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFolders: ListProperty<Directory>

    @get:OutputFile
    abstract val engineInputPropertiesFiles: RegularFileProperty

    @get:OutputFile
    abstract val logFile: RegularFileProperty

    @get:OutputFile
    abstract val streamingOutputFile: RegularFileProperty

    @get:OutputDirectory
    abstract val resultsDir: DirectoryProperty

    @get:OutputDirectory
    abstract val coverageDir: DirectoryProperty

    @get:Nested
    abstract val deviceProviderFactory: DeviceProviderFactory

    /**
     * Specifies the target devices for test execution using a comma-separated list of serial numbers.
     *
     * When this property is set, tests will run only on the devices corresponding to the given
     * serials. If this property is not provided or is null, tests will be executed on all currently
     * connected and online devices.
     */
    @get:Input
    @get:Optional
    abstract val androidDeviceSerials: Property<String>

    @get:Nested
    abstract val managedDevices: ListProperty<ManagedVirtualDevice>

    @get:Internal
    abstract val avdService: Property<AvdComponentsBuildService>

    @TaskAction
    override fun executeTests() {

        // I suspect Gradle considers that it is the responsibility of the test engine to clean
        // up the output folders but the fact is that we never run in incremental mode so keeping
        // the previous runs output folders is of little to no interest, I will therefore clean
        // up those folders, although we should check with Gradle what is their official policy.
        // In fact, I am suspecting that since the test engine is running in a separate process,
        // Gradle has no way to monitor what is written to these directories so it leaves it
        // untouched at the next execution.
        PathUtils.deleteRecursivelyIfExists(resultsDir.get().asFile.toPath())
        PathUtils.deleteRecursivelyIfExists(coverageDir.get().asFile.toPath())

        val engineInputParameters: List<TestEngineInputProperty> = engineInputParameters.get(). map { inputProperty ->
            TestEngineInputProperty(
                inputProperty.type.propertyName,
                inputProperty.value.get().asFile.absolutePath
            )
        }

        // only get the connected devices if the test requested an APK.
        if (
            engineInputParameters.any { inputParameter ->
                inputParameter.name == AgpTestSuiteInputParameters.TESTED_APKS.propertyName
            }
        ) {
            provisionDevicesAndExecute { onlineDeviceSerials ->
                executeTests(engineInputParameters, onlineDeviceSerials)
            }
        } else {
            executeTests(engineInputParameters)
        }
    }

    private fun provisionDevicesAndExecute(executeTestFunc: (onlineDeviceSerials: String) -> Unit) {
        provisionConnectedDevicesAndExecute { connectedDeviceSerials ->
            provisionManagedDevicesAndExecute { managedDeviceSerials ->
                val onlineDeviceSerials =
                    (connectedDeviceSerials + managedDeviceSerials).joinToString(",")
                executeTestFunc(onlineDeviceSerials)
            }
        }
    }

    private fun provisionConnectedDevicesAndExecute(onDevicesReady: (onlineDeviceSerials: List<String>) -> Unit) {
        val deviceProvider = deviceProviderFactory.getDeviceProvider(
            buildTools.adbExecutable(),
            androidDeviceSerials.orNull,
        )
        try {
            deviceProvider.use {
                val onlineDeviceSerials = deviceProvider.devices.map {
                    it.serialNumber
                }
                onDevicesReady(onlineDeviceSerials)
            }
        } catch (_: DeviceException) {
            onDevicesReady(listOf())
        }
    }

    private fun provisionManagedDevicesAndExecute(onDevicesReady: (onlineDeviceSerials: List<String>) -> Unit) {
        provisionManagedDevicesAndExecute(
            managedDevices.get().asIterable().iterator(),
            mutableListOf(),
            onDevicesReady,
        )
    }

    private fun provisionManagedDevicesAndExecute(
        iterator: Iterator<ManagedVirtualDevice>,
        onlineDeviceSerials: MutableList<String>,
        onDevicesReady: (onlineDeviceSerials: List<String>) -> Unit) {
        if (!iterator.hasNext()) {
            onDevicesReady(onlineDeviceSerials)
            return
        }
        val avdName = computeAvdName(iterator.next())
        avdService.get().runWithAvd(avdName) { onlineDeviceSerial ->
            onlineDeviceSerials += onlineDeviceSerial
            provisionManagedDevicesAndExecute(iterator, onlineDeviceSerials, onDevicesReady)
        }
    }

    private fun executeTests(
        engineInputParameters: List<TestEngineInputProperty>,
        onlineDeviceSerials: String? = null,
    ) {
        val standardInputs = mutableListOf(
            TestEngineInputProperty(
                TestEngineInputProperty.SOURCE_FOLDERS,
                sourceFolders.get()
                    .joinToString(separator = File.separator) { it.asFile.absolutePath }
            ),
            TestEngineInputProperty(
                TestEngineInputProperty.LOGGING_FILE,
                providerToPath(logFile)
            ),
            TestEngineInputProperty(
                TestEngineInputProperty.STREAMING_FILE,
                providerToPath(streamingOutputFile)
            ),
            TestEngineInputProperty(
                TestEngineInputProperty.RESULTS_DIR,
                providerToPath(resultsDir)
            ),
            TestEngineInputProperty(
                TestEngineInputProperty.COVERAGE_DIR,
                providerToPath(coverageDir)
            ),
            TestEngineInputProperty(
                AgpTestSuiteInputParameters.ADB_EXECUTABLE.propertyName,
                buildTools.adbExecutable().get().asFile.absolutePath
            ),
        )

        if (onlineDeviceSerials != null) {
            standardInputs.add(
                TestEngineInputProperty(
                    TestEngineInputProperty.SERIAL_IDS,
                    onlineDeviceSerials
                )
            )
        }

        // write all the input properties for the junit engine. This mean the input properties
        // that were requested through the TestSuite DSL/Variant APIs but also the default ones
        // that are always provided.
        AgpTestSuiteInputsSerializer.serialize(
            engineInputProperties = engineInputProperties.get(),
            engineInputParameters = engineInputParameters.plus(standardInputs),
            into = engineInputPropertiesFiles.asFile.get(),
        )

        super.executeTests()

        // Read the junit engine logging file and output it.
        // This is probably a temporary solution until something better is figured out.
        if (logFile.get().asFile.exists()) {
            this.logger.info(logFile.get().asFile.readText())
        }
    }

    private fun providerToPath(value: Provider<out FileSystemLocation>): String =
        value.get().asFile.absolutePath

    class CreationAction(
        val creationConfig: TestSuiteCreationConfig,
        val testSuiteTarget: TestSuiteTargetCreationConfig
    ): GlobalTaskCreationAction<TestSuiteTestTask>() {

        override val name: String
            get() = testSuiteTarget.testTaskName

        override val type: Class<TestSuiteTestTask> = TestSuiteTestTask::class.java

        override fun configure(task: TestSuiteTestTask) {
            super.configure(task)
            task.group = JavaBasePlugin.VERIFICATION_GROUP
            task.outputs.upToDateWhen { false }

            val classesDir = task.project.layout.buildDirectory.file(task.name)
            UniqueClassGenerator().generateSimpleClass(classesDir.get().asFile)
            task.testClassesDirs =  creationConfig.services.fileCollection().also {
                it.from(classesDir)
            }
            task.classpath = creationConfig.services.fileCollection().also {
                it.from(classesDir)
                creationConfig.sources.forEach { sourceContainer: TestSuiteSourceContainer ->
                    it.from(sourceContainer.dependencies.runtimeClasspath)
                }
            }

            // Get all project properties, and system properties (possibly overriding project
            // properties) and register them for the test engine.
            creationConfig.services.projectOptions.get(
                StringOption.TEST_SUITE_TEST_TASK_ADDITIONAL_INPUTS_FILE
            )?.let { additionalInputFilePath ->
                task.systemProperty(
                    StringOption.TEST_SUITE_TEST_TASK_ADDITIONAL_INPUTS_FILE.propertyName,
                    additionalInputFilePath
                )
                // add the file to the task's inputs.
                task.inputs.file(additionalInputFilePath)
            }

            task.androidDeviceSerials.setDisallowChanges(
                task.project.providers.environmentVariable("ANDROID_SERIAL"))

            val localDevices = creationConfig.global.androidTestOptions.managedDevices.localDevices
            testSuiteTarget.targetDevices.forEach {
                task.managedDevices.add(localDevices.getByName(it) as ManagedVirtualDevice)
            }
            task.managedDevices.disallowChanges()

            val junitEngineSpec = (creationConfig.junitEngineSpec as JUnitEngineSpecImplForVariant)
            junitEngineSpec.inputs.forEach { inputParameter: AgpTestSuiteInputParameters ->
                when (inputParameter) {
                    AgpTestSuiteInputParameters.MERGED_MANIFEST -> {
                        task.engineInputParameters.add(
                            AgpTestSuiteInputParameter(
                                AgpTestSuiteInputParameters.MERGED_MANIFEST,
                                creationConfig.testedVariant.artifacts.get(
                                    SingleArtifact.MERGED_MANIFEST
                                )
                            )
                        )
                    }

                    AgpTestSuiteInputParameters.TESTED_APKS -> {
                        task.engineInputParameters.add(
                            AgpTestSuiteInputParameter(
                                AgpTestSuiteInputParameters.TESTED_APKS,
                                creationConfig.testedVariant.artifacts.get(
                                    SingleArtifact.APK
                                )
                            )
                        )
                    }
                    AgpTestSuiteInputParameters.ADB_EXECUTABLE -> {
                        // do nothing so far, we always do it but it might change in the near future.
                    }

                    else -> {
                        println("I don't know of this parameter $inputParameter")
                    }
                }
            }

            // always wire adb inputs
            // TODO: Find ways to do this on demand.
            task.buildTools.initialize(
                task,
                creationConfig.services.buildServiceRegistry,
                creationConfig.global.compileSdkHashString,
                creationConfig.global.buildToolsRevision
            )

            task.engineInputProperties.set(junitEngineSpec.inputProperties)
            // add default properties.
            task.engineInputProperties.put(
                TestEngineInputProperty.TESTED_APPLICATION_ID,
                creationConfig.testedVariant.applicationId
            )

            task.useJUnitPlatform { testFramework: JUnitPlatformOptions ->
                testFramework.includeEngines(*creationConfig.junitEngineSpec.includeEngines.toTypedArray())
                testFramework.excludeEngines("junit-jupiter")
            }
            creationConfig.sources.forEach { sourceContainer: TestSuiteSourceContainer ->
                val sourceSet =  sourceContainer.source
                when (sourceSet) {
                    is TestSuiteSourceSet.Assets -> {
                        task.sourceFolders.addAll(sourceSet.get().all)
                        task.failOnNoDiscoveredTests.set(false)
                    }
                    is TestSuiteSourceSet.HostJar ->
                        task.sourceFolders.addAll(sourceSet.get().all)
                    is TestSuiteSourceSet.TestApk ->
                        throw RuntimeException("Not implemented")
                }
            }

            task.engineInputParameters.disallowChanges()
            // TODO : Improve file handling by using Artifacts APIs.
            task.engineInputPropertiesFiles.set(
                task.project.layout.buildDirectory
                    .file("intermediates/${creationConfig.testedVariant.name}/$name/junit_inputs.txt")
            )
            task.logFile.set(
                task.project.layout.buildDirectory
                    .file("intermediates/${creationConfig.testedVariant.name}/$name/junit_engines_logging.txt")
            )
            task.streamingOutputFile.set(
                task.project.layout.buildDirectory
                    .file("intermediates/${creationConfig.testedVariant.name}/$name/streaming.txt")
            )
            task.resultsDir.set(
                task.project.layout.buildDirectory
                    .dir("intermediates/${creationConfig.testedVariant.name}/$name/results")
            )
            task.coverageDir.set(
                task.project.layout.buildDirectory
                    .dir("intermediates/${creationConfig.testedVariant.name}/$name/coverage_data")
            )
            task.environment(DEFAULT_ENV_VARIABLE, task.engineInputPropertiesFiles.get().asFile.absolutePath)
            task.environment("junit.platform.commons.logging.level","debug")
            task.deviceProviderFactory.timeOutInMs.set(10000)

            task.avdService.setDisallowChanges(getBuildService(creationConfig.services.buildServiceRegistry))

            // TODO : Provide this as a DSL setting
            val debugJunitEngine = System.getenv("DEBUG_JUNIT_ENGINE")
            if (!debugJunitEngine.isNullOrEmpty()) {
                val serverArg: String =
                    if (debugJunitEngine.equals("socket-listen", ignoreCase = true)) "n" else "y"
                task.jvmArgs("-agentlib:jdwp=transport=dt_socket,server=$serverArg,suspend=y,address=5006")
            }
        }
    }

    class AgpTestSuiteInputParameter(
        @get:Input
        val type: AgpTestSuiteInputParameters,

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        val value: Provider<out FileSystemLocation>
    )

    class AgpTestSuiteInputsSerializer {
        companion object {
            fun serialize(
                engineInputParameters: List<TestEngineInputProperty>,
                engineInputProperties: Map<String, String>,
                into: File
            ) {
                val properties = Properties()
                engineInputParameters
                    .plus(
                        engineInputProperties.map {
                            TestEngineInputProperty(it.key, it.value)
                        }
                    )
                    .forEach { testEngineInputProperty ->
                        properties.put(testEngineInputProperty.name, testEngineInputProperty.value)
                    }
                properties.store(FileWriter(into), "Input properties for test engine")
            }
        }
    }
}
