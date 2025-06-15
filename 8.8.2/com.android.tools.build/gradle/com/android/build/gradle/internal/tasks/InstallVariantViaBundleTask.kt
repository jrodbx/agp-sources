/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.BuildToolsExecutableInput
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.initialize
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.testing.ConnectedDeviceProvider
import com.android.build.gradle.internal.utils.BundleApkFetcher
import com.android.build.gradle.internal.utils.SdkApkInstallGroup
import com.android.build.gradle.internal.utils.ViaBundleDeviceApkOutput
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.testing.api.DeviceConnector
import com.android.builder.testing.api.DeviceProvider
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import com.android.utils.ILogger
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.stream.Collectors

/**
 * Task installing an app variant. It looks at connected device and install the best matching
 * variant output on each device.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.DEPLOYMENT)
abstract class InstallVariantViaBundleTask : NonIncrementalTask() {

    private var minSdkVersion = 0
    private var minSdkCodename: String? = null
    private var timeOutInMs = 0

    private var installOptions = mutableListOf<String>()

    @get:Nested
    abstract val buildTools: BuildToolsExecutableInput

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val apkBundle: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val privacySandboxSdkApksFiles: ConfigurableFileCollection

    init {
        this.outputs.upToDateWhen { false }
    }

    override fun doTaskAction() {

        workerExecutor.noIsolation().submit(InstallRunnable::class.java) {
            it.initializeFromBaseTask(this)
            it.adbExe.set(buildTools.adbExecutable())
            it.apkBundle.set(apkBundle.get().asFile)
            it.timeOutInMs.set(timeOutInMs)
            it.installOptions.set(installOptions)
            it.variantName.set(variantName)
            it.minApiCodeName.set(minSdkCodename)
            it.minSdkVersion.set(minSdkVersion)
            it.privacySandboxSdkApksFiles.setFrom(privacySandboxSdkApksFiles)
        }
    }

    abstract class Params : ProfileAwareWorkAction.Parameters() {
        abstract val adbExe: RegularFileProperty
        abstract val apkBundle: RegularFileProperty
        abstract val timeOutInMs: Property<Int>
        abstract val installOptions: ListProperty<String>
        abstract val variantName: Property<String>
        abstract val minApiCodeName: Property<String?>
        abstract val minSdkVersion: Property<Int>
        abstract val privacySandboxSdkApksFiles: ConfigurableFileCollection
    }

    abstract class InstallRunnable : ProfileAwareWorkAction<Params>(), BundleApkFetcher {
        override fun run() {
            val logger: Logger = Logging.getLogger(InstallVariantViaBundleTask::class.java)
            val iLogger = LoggerWrapper(logger)
            val deviceProvider = createDeviceProvider(iLogger)
            val androidVersion = AndroidVersion(parameters.minSdkVersion.get(), parameters.minApiCodeName.orNull)
            val apkOutput = ViaBundleDeviceApkOutput(
                parameters.apkBundle,
                androidVersion,
                parameters.privacySandboxSdkApksFiles,
                parameters.variantName.get(),
                parameters.projectPath.get(),
                this
            )

            deviceProvider.use {
                var successfulInstallCount = 0
                val devices = deviceProvider.devices

                for (device in devices) {
                    val apkInstallGroups = apkOutput.getApks(com.android.build.gradle.internal.utils.getDeviceSpec(device))
                    if (apkInstallGroups.isEmpty()) {
                        continue
                    }
                    for (apkInstallGroup in apkInstallGroups) {
                        val apkFiles =
                            apkInstallGroup.apks.stream()
                                .map { obj: RegularFile -> obj.asFile }
                                .collect(Collectors.toList())
                        if (apkInstallGroup is SdkApkInstallGroup) {
                            val apks = apkInstallGroup.apks.map { it.asFile }
                            logger.lifecycle(
                                "Installing privacy sandbox SDK APKs '{}' on '{}' for {}:{}",
                                FileUtils.getNamesAsCommaSeparatedList(apks),
                                device.name,
                                parameters.projectPath.get(),
                                parameters.variantName.get()
                            )
                            installPackages(device, apks, iLogger)
                            successfulInstallCount++
                        } else {
                            if(apkFiles.isEmpty()) {
                                logger.lifecycle(
                                    "Skipping device '{}' for '{}:{}': No APK generated",
                                    device.name,
                                    parameters.projectPath.get(),
                                    parameters.variantName.get())
                            } else {
                                // install them.
                                logger.lifecycle(
                                    "Installing APKs '{}' on '{}' for {}:{}",
                                    FileUtils.getNamesAsCommaSeparatedList(apkFiles),
                                    device.name,
                                    parameters.projectPath.get(),
                                    parameters.variantName.get()
                                )
                                installPackages(device, apkFiles, iLogger)
                                successfulInstallCount++
                            }
                        }
                    }
                }

                if (successfulInstallCount == 0) {
                    throw GradleException("Failed to install on any devices.")
                } else {
                    logger.quiet(
                        "Installed on {} {}.",
                        successfulInstallCount,
                        if (successfulInstallCount == 1) "device" else "devices"
                    )
                }
            }
        }

        private fun installPackages(device: DeviceConnector, apkFiles: List<File>, iLogger: ILogger) {
            if (apkFiles.size > 1) {
                device.installPackages(apkFiles, parameters.installOptions.get(), parameters.timeOutInMs.get(), iLogger)
            } else {
                device.installPackage(apkFiles[0], parameters.installOptions.get(), parameters.timeOutInMs.get(), iLogger)
            }
        }

        protected open fun createDeviceProvider(iLogger: ILogger): DeviceProvider =
            ConnectedDeviceProvider(
                parameters.adbExe.get().asFile,
                parameters.timeOutInMs.get(),
                iLogger,
                java.lang.System.getenv("ANDROID_SERIAL")
            )
     }

    internal class CreationAction(creationConfig: ApkCreationConfig) :
        VariantTaskCreationAction<InstallVariantViaBundleTask, ApkCreationConfig>(
            creationConfig
        ) {

        override val name: String
            get() = computeTaskName("install")
        override val type: Class<InstallVariantViaBundleTask>
            get() = InstallVariantViaBundleTask::class.java

        override fun configure(
            task: InstallVariantViaBundleTask
        ) {
            super.configure(task)

            task.description = "Installs the " + creationConfig.description + ""
            task.group = TaskManager.INSTALL_GROUP

            creationConfig.minSdk.let {
                task.minSdkVersion = it.apiLevel
                task.minSdkCodename = it.codename
            }
            creationConfig.global.installationOptions.installOptions.let {
                task.installOptions.addAll(it)
            }

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.APKS_FROM_BUNDLE,
                task.apkBundle
            )

            task.timeOutInMs = creationConfig.global.installationOptions.timeOutInMs
            task.buildTools.initialize(task, creationConfig)
            if (creationConfig.privacySandboxCreationConfig != null) {
                task.privacySandboxSdkApksFiles.setFrom(
                    creationConfig.variantDependencies
                        .getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_APKS
                        )
                )
            }
            task.privacySandboxSdkApksFiles.disallowChanges()
        }

        override fun handleProvider(
            taskProvider: TaskProvider<InstallVariantViaBundleTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.taskContainer.installTask = taskProvider
        }
    }
}
