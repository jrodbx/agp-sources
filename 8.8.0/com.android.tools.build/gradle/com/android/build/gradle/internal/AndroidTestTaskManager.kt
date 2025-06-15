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

package com.android.build.gradle.internal

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.impl.InternalScopedArtifacts
import com.android.build.gradle.internal.component.DeviceTestCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.KmpComponentCreationConfig
import com.android.build.gradle.internal.coverage.JacocoConfigurations
import com.android.build.gradle.internal.coverage.JacocoReportTask
import com.android.build.gradle.internal.dsl.ManagedVirtualDevice
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.LintModelWriterTask
import com.android.build.gradle.internal.plugins.LINT_PLUGIN_ID
import com.android.build.gradle.internal.profile.AnalyticsConfiguratorService
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.AndroidReportTask
import com.android.build.gradle.internal.tasks.AppClasspathCheckTask
import com.android.build.gradle.internal.tasks.CompressAssetsTask
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask
import com.android.build.gradle.internal.tasks.DeviceSerialTestTask
import com.android.build.gradle.internal.tasks.JacocoTask
import com.android.build.gradle.internal.tasks.ManagedDeviceCleanTask
import com.android.build.gradle.internal.tasks.ManagedDeviceInstrumentationTestSetupTask
import com.android.build.gradle.internal.tasks.ManagedDeviceSetupTask
import com.android.build.gradle.internal.tasks.SigningConfigVersionsWriterTask
import com.android.build.gradle.internal.tasks.SigningConfigWriterTask
import com.android.build.gradle.internal.tasks.StripDebugSymbolsTask
import com.android.build.gradle.internal.tasks.TestPreBuildTask
import com.android.build.gradle.internal.tasks.TestServerTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.android.build.gradle.internal.tasks.featuresplit.getFeatureName
import com.android.build.gradle.internal.test.AbstractTestDataImpl
import com.android.build.gradle.internal.test.BundleTestDataImpl
import com.android.build.gradle.internal.test.TestDataImpl
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.BooleanOption.LINT_ANALYSIS_PER_COMPONENT
import com.android.builder.core.BuilderConstants.FD_MANAGED_DEVICE_SETUP_RESULTS
import com.android.builder.core.ComponentType
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableSet
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

class AndroidTestTaskManager(
    project: Project,
    globalConfig: GlobalTaskCreationConfig
): TaskManager(project, globalConfig) {

    fun createTopLevelTasks() {
        createMockableJarTask()
        val reportTasks: MutableList<String> = mutableListOf()
        val providers = globalConfig.deviceProviders

        // If more than one flavor, create a report aggregator task and make this the parent
        // task for all new connected tasks.  Otherwise, create a top level connectedAndroidTest
        // Task.
        val connectedAndroidTestTask: TaskProvider<out Task>
        if (globalConfig.productFlavorCount > 0) {
            connectedAndroidTestTask = taskFactory.register(
                AndroidReportTask.CreationAction(
                    globalConfig,
                    AndroidReportTask.CreationAction.TaskKind.CONNECTED))
            reportTasks.add(connectedAndroidTestTask.name)
        } else {
            connectedAndroidTestTask = taskFactory.register(
                CONNECTED_ANDROID_TEST
            ) { connectedTask: Task ->
                connectedTask.group = JavaBasePlugin.VERIFICATION_GROUP
                connectedTask.description = (
                        "Installs and runs instrumentation tests "
                                + "for all flavors on connected devices.")
            }
        }
        taskFactory.configure(globalConfig.taskNames.connectedCheck) {
            it.dependsOn(connectedAndroidTestTask.name)
        }
        val deviceAndroidTestTask: TaskProvider<out Task>
        // if more than one provider tasks, either because of several flavors, or because of
        // more than one providers, then create an aggregate report tasks for all of them.
        if (providers.size > 1 || globalConfig.productFlavorCount > 0) {
            deviceAndroidTestTask = taskFactory.register(
                AndroidReportTask.CreationAction(
                    globalConfig,
                    AndroidReportTask.CreationAction.TaskKind.DEVICE_PROVIDER))
            reportTasks.add(deviceAndroidTestTask.name)
        } else {
            deviceAndroidTestTask = taskFactory.register(
                DEVICE_ANDROID_TEST
            ) { providerTask: Task ->
                providerTask.group = JavaBasePlugin.VERIFICATION_GROUP
                providerTask.description = (
                        "Installs and runs instrumentation tests "
                                + "using all Device Providers.")
            }
        }
        taskFactory.configure(globalConfig.taskNames.deviceCheck) {
            it.dependsOn(deviceAndroidTestTask.name)
        }

        // If gradle is launched with --continue, we want to run all tests and generate an
        // aggregate report (to help with the fact that we may have several build variants, or
        // or several device providers).
        // To do that, the report tasks must run even if one of their dependent tasks (flavor
        // or specific provider tasks) fails, when --continue is used, and the report task is
        // meant to run (== is in the task graph).
        // To do this, we make the children tasks ignore their errors (ie they won't fail and
        // stop the build).
        //TODO: move to mustRunAfter once is stable.
        if (reportTasks.isNotEmpty() && project.gradle.startParameter
                .isContinueOnFailure) {
            project.gradle
                .taskGraph
                .whenReady { taskGraph: TaskExecutionGraph ->
                    for (reportTask in reportTasks) {
                        if (taskGraph.hasTask(getTaskPath(project, reportTask))) {
                            taskFactory.configure(
                                reportTask
                            ) { task: Task -> (task as AndroidReportTask).setWillRun() }
                        }
                    }
                }
        }

        // Create tasks to manage test devices.
        createTestDevicesTasks()
    }

    /** Creates the tasks to build android tests.  */
    fun createTasks(androidTestProperties: DeviceTestCreationConfig) {
        createAnchorTasks(androidTestProperties)

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(androidTestProperties)

        // Add a task to process the manifest
        createProcessTestManifestTask(androidTestProperties)

        // Add a task to create the res values
        createGenerateResValuesTask(androidTestProperties)

        // Add a task to compile renderscript files.
        createRenderscriptTask(androidTestProperties)

        // Add a task to merge the resource folders
        createMergeResourcesTask(androidTestProperties, true, ImmutableSet.of())

        // Add a task to package the resource folders
        createPackageResourcesTask(androidTestProperties)

        // Add tasks to compile shader
        createShaderTask(androidTestProperties)

        // Add a task to merge the assets folders
        createMergeAssetsTask(androidTestProperties)
        taskFactory.register(CompressAssetsTask.CreationAction(androidTestProperties))

        // Add a task to create the BuildConfig class
        createBuildConfigTask(androidTestProperties)

        // Add a task to generate resource source files
        createApkProcessResTask(androidTestProperties)

        // process java resources
        createProcessJavaResTask(androidTestProperties)
        createAidlTask(androidTestProperties)

        // add tasks to merge jni libs.
        createMergeJniLibFoldersTasks(androidTestProperties)

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(androidTestProperties)

        if (androidTestProperties !is KmpComponentCreationConfig) {
            // Add a task to compile the test application
            setJavaCompilerTask(createJavacTask(androidTestProperties), androidTestProperties)
        }
        createPostCompilationTasks(androidTestProperties)

        // Add tasks to produce the signing config files
        createValidateSigningTask(androidTestProperties)
        taskFactory.register(SigningConfigWriterTask.CreationAction(androidTestProperties))
        taskFactory.register(SigningConfigVersionsWriterTask.CreationAction(androidTestProperties))
        taskFactory.register(StripDebugSymbolsTask.CreationAction(androidTestProperties))
        createPackagingTask(androidTestProperties)
        taskFactory.configure(
            ASSEMBLE_ANDROID_TEST
        ) { assembleTest: Task ->
            assembleTest.dependsOn(
                androidTestProperties
                    .taskContainer
                    .assembleTask
                    .name)
        }

        // Create lint tasks for a KMP component only if the lint standalone plugin is applied (to
        // avoid Android-specific behavior)
        val isKmpPerComponentLintAnalysis =
            androidTestProperties is KmpComponentCreationConfig
                    && project.plugins.hasPlugin(LINT_PLUGIN_ID)
        val isNonKmpPerComponentLintAnalysis =
            androidTestProperties !is KmpComponentCreationConfig
                    && androidTestProperties.services.projectOptions.get(LINT_ANALYSIS_PER_COMPONENT)
        val isPerComponentLintAnalysis =
            isKmpPerComponentLintAnalysis || isNonKmpPerComponentLintAnalysis
        if (globalConfig.avoidTaskRegistration.not()
            && isPerComponentLintAnalysis
            && globalConfig.lintOptions.ignoreTestSources.not()
        ) {
            taskFactory.register(
                AndroidLintAnalysisTask.PerComponentCreationAction(
                    androidTestProperties,
                    fatalOnly = false
                )
            )
            taskFactory.register(
                LintModelWriterTask.PerComponentCreationAction(
                    androidTestProperties,
                    useModuleDependencyLintModels = false,
                    fatalOnly = false,
                    isMainModelForLocalReportTask = false
                )
            )
        }

        createConnectedTestForVariant(androidTestProperties)
    }

    private fun createPackageResourcesTask(creationConfig: DeviceTestCreationConfig) {
        val projectOptions = creationConfig.services.projectOptions
        val appCompileRClass = projectOptions[BooleanOption.ENABLE_APP_COMPILE_TIME_R_CLASS]

        if (isTestApkCompileRClassEnabled(appCompileRClass, creationConfig.componentType)) {
            // The "small merge" of only the android tests resources
            basicCreateMergeResourcesTask(
                creationConfig,
                MergeType.PACKAGE,
                false,
                false,
                false,
                ImmutableSet.of(),
                null
            )
        }
    }
    private fun isTestApkCompileRClassEnabled(
        compileRClassFlag: Boolean,
        componentType: ComponentType
    ): Boolean {
        return compileRClassFlag
            && componentType.isForTesting
            && componentType.isApk
    }


    private fun createConnectedTestForVariant(androidTestProperties: DeviceTestCreationConfig) {
        val testedVariant = androidTestProperties.mainVariant
        val isLibrary = testedVariant.componentType.isAar

        val privacySandboxSdkApks = androidTestProperties.privacySandboxCreationConfig?.let {
            testedVariant
                    .variantDependencies
                    .getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_APKS)
        }

        val privacySandboxCompatSdkApks = androidTestProperties.privacySandboxCreationConfig?.let {
            testedVariant.artifacts.get(InternalArtifactType.EXTRACTED_SDK_APKS)
        }

        val testData: AbstractTestDataImpl = if (testedVariant.componentType.isDynamicFeature) {
            BundleTestDataImpl(
                androidTestProperties.namespace,
                androidTestProperties,
                androidTestProperties.artifacts.get(SingleArtifact.APK),
                getFeatureName(project.path),
                testedVariant
                    .variantDependencies
                    .getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        AndroidArtifacts.ArtifactType.APKS_FROM_BUNDLE),
                privacySandboxSdkApks,
                privacySandboxCompatSdkApks,
                androidTestProperties
                    .services
                    .projectOptions
                    .extraInstrumentationTestRunnerArgs)
        } else {
            TestDataImpl(
                androidTestProperties.namespace,
                androidTestProperties,
                androidTestProperties.artifacts.get(SingleArtifact.APK),
                if (isLibrary) null else testedVariant.artifacts.get(SingleArtifact.APK),
                privacySandboxSdkApks,
                privacySandboxCompatSdkApks,
                testedVariant.artifacts.get(InternalArtifactType.USES_SDK_LIBRARY_SPLIT_FOR_LOCAL_DEPLOYMENT),
                androidTestProperties
                    .services
                    .projectOptions
                    .extraInstrumentationTestRunnerArgs
            )
        }

        configureTestData(androidTestProperties, testData)


        val connectedCheckSerials: Provider<List<String>> =
            taskFactory.named(globalConfig.taskNames.connectedCheck).flatMap { test ->
                (test as DeviceSerialTestTask).serialValues
            }
        val connectedTask = taskFactory.register(
            DeviceProviderInstrumentTestTask.CreationAction(
                androidTestProperties, testData, connectedCheckSerials))
        taskFactory.configure(
            CONNECTED_ANDROID_TEST
        ) { connectedAndroidTest: Task -> connectedAndroidTest.dependsOn(connectedTask) }
        if (androidTestProperties.codeCoverageEnabled) {
            val jacocoAntConfiguration = JacocoConfigurations.getJacocoAntTaskConfiguration(
                project, JacocoTask.getJacocoVersion(androidTestProperties))
            val reportTask = taskFactory.register(
                JacocoReportTask.CreationActionConnectedTest(
                    androidTestProperties, jacocoAntConfiguration))
            testedVariant.taskContainer.coverageReportTask.dependsOn(reportTask)
            taskFactory.configure(
                CONNECTED_ANDROID_TEST
            ) { connectedAndroidTest: Task -> connectedAndroidTest.dependsOn(reportTask) }
        }
        val providers = globalConfig.deviceProviders
        if (providers.isNotEmpty()) {
            getBuildService(project.gradle.sharedServices, AnalyticsConfiguratorService::class.java)
                .get()
                .getProjectBuilder(project.path)
                ?.projectApiUseBuilder
                ?.builderTestApiDeviceProvider = true
        }
        // now the providers.
        for (deviceProvider in providers) {
            val providerTask = taskFactory.register(
                DeviceProviderInstrumentTestTask.CreationAction(
                    androidTestProperties, deviceProvider, testData, connectedCheckSerials))
            taskFactory.configure(
                DEVICE_ANDROID_TEST
            ) { deviceAndroidTest: Task -> deviceAndroidTest.dependsOn(providerTask) }
        }

        // now the test servers
        val servers = globalConfig.testServers
        if (servers.isNotEmpty()) {
            getBuildService(project.gradle.sharedServices, AnalyticsConfiguratorService::class.java)
                .get()
                .getProjectBuilder(project.path)
                ?.projectApiUseBuilder
                ?.builderTestApiTestServer = true
        }
        for (testServer in servers) {
            val serverTask = taskFactory.register(
                TestServerTask.TestServerTaskCreationAction(
                    androidTestProperties, testServer
                )
            )
            serverTask.dependsOn<Task>(androidTestProperties.taskContainer.assembleTask)
            taskFactory.configure(globalConfig.taskNames.deviceCheck) {
                it.dependsOn(serverTask)
            }
        }

        createTestDevicesForVariant(
            androidTestProperties,
            testData,
            androidTestProperties.mainVariant.name
        )
    }

    private fun createTestDevicesTasks() {
        if (globalConfig.androidTestOptions.managedDevices.devices.isEmpty()) {
            return
        }

        val managedDevices = getManagedDevices()
        val cleanTask = taskFactory.register(
            ManagedDeviceCleanTask.CreationAction(
                "cleanManagedDevices",
                globalConfig,
                managedDevices.filterIsInstance<ManagedVirtualDevice>()))
        val allDevices = taskFactory.register(
            globalConfig.taskNames.allDevicesCheck
        ) { allDevicesCheckTask: Task ->
            allDevicesCheckTask.description =
                "Runs all device checks on all managed devices defined in the TestOptions dsl."
            allDevicesCheckTask.group = JavaBasePlugin.VERIFICATION_GROUP
        }

        for (device in managedDevices) {
            val registration = globalConfig.managedDeviceRegistry.get(device.javaClass)

            val setupTask = when {
                device is ManagedVirtualDevice -> taskFactory.register(
                    ManagedDeviceInstrumentationTestSetupTask.CreationAction(
                        setupTaskName(device),
                        device,
                        globalConfig))
                registration != null -> {
                    if (registration.hasSetupActions) {
                        taskFactory.register(
                            ManagedDeviceSetupTask.CreationAction(
                                project.layout.buildDirectory.dir(
                                    FileUtils.join(FD_MANAGED_DEVICE_SETUP_RESULTS, device.name)
                                ),
                                registration.setupConfigAction!!,
                                registration.setupTaskAction!!,
                                device,
                                globalConfig,

                            )
                        )
                    } else {
                        taskFactory.register(setupTaskName(device))
                    }
                }
                else -> {
                    taskFactory.register(setupTaskName(device))
                }
            }
            setupTask.configure {
                it.mustRunAfter(cleanTask)
            }

            val deviceAllVariantsTask = taskFactory.register(
                managedDeviceAllVariantsTaskName(device)
            ) { deviceVariantTask: Task ->
                deviceVariantTask.description =
                    "Runs all device checks on the managed device ${device.name}."
                deviceVariantTask.group = JavaBasePlugin.VERIFICATION_GROUP
            }
            allDevices.dependsOn(deviceAllVariantsTask)
        }

        for (group in getDeviceGroups()) {
            taskFactory.register(
                managedDeviceGroupAllVariantsTaskName(group)
            ) { deviceGroupTask: Task ->
                deviceGroupTask.description =
                    "Runs all device checks on all devices defined in group ${group.name}."
                deviceGroupTask.group = JavaBasePlugin.VERIFICATION_GROUP
            }
        }
    }

    private fun createMockableJarTask() {
        addAndroidJarDependency()
        // Adding this task to help the IDE find the mockable JAR.
        taskFactory.register(
            globalConfig.taskNames.createMockableJar
        ) { task: Task ->
            task.dependsOn(globalConfig.mockableJarArtifact)
        }
    }

    override val javaResMergingScopes = setOf(
        InternalScopedArtifacts.InternalScope.SUB_PROJECTS,
        InternalScopedArtifacts.InternalScope.EXTERNAL_LIBS
    )

    override fun createVariantPreBuildTask(creationConfig: ComponentCreationConfig) {
        if (creationConfig is DeviceTestCreationConfig && // should always be true
            creationConfig.mainVariant.componentType.isApk &&
            !creationConfig.mainVariant.componentType.isForTesting) {

            val useDependencyConstraints = creationConfig
                .services
                .projectOptions[BooleanOption.USE_DEPENDENCY_CONSTRAINTS]

            val testPreBuildTask = taskFactory.register(
                TestPreBuildTask.CreationAction(creationConfig)
            )
            if (useDependencyConstraints) {
                testPreBuildTask.configure { t: Task? -> t!!.enabled = false }
            } else {
                val classpathCheck = taskFactory.register(
                    AppClasspathCheckTask.CreationAction(creationConfig)
                )
                testPreBuildTask.dependsOn(classpathCheck)
            }

            return
        }

        super.createVariantPreBuildTask(creationConfig)
    }
}
