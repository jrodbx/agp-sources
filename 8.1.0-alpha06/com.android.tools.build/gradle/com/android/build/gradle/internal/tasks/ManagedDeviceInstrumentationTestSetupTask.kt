/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.build.api.dsl.TestOptions
import com.android.build.gradle.internal.AvdComponentsBuildService
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.SdkComponentsBuildService.VersionedSdkLoader
import com.android.build.gradle.internal.computeAbiFromArchitecture
import com.android.build.gradle.internal.computeAvdName
import com.android.build.gradle.internal.computeManagedDeviceEmulatorMode
import com.android.build.gradle.internal.dsl.ManagedVirtualDevice
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.testing.utp.ManagedDeviceImageSuggestionGenerator
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.repository.Revision
import com.android.testing.utils.computeSystemImageHashFromDsl
import com.android.testing.utils.isWearTvOrAutoDevice
import com.android.testing.utils.isWearTvOrAutoSource
import com.android.utils.osArchitecture
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.work.DisableCachingByDefault

private const val SYSTEM_IMAGE_PREFIX = "system-images;"
private const val HASH_DIVIDER = ";"
private const val WAIT_AFTER_BOOT_MS = 5000L
private const val DEVICE_BOOT_TIMEOUT_SEC = 80L

private val loggerWrapper = LoggerWrapper.getLogger(ManagedDeviceInstrumentationTestSetupTask::class.java)

/**
 * Task to create an AVD from a managed device definition in the DSL.
 *
 * Expands the dsl from a [ManagedVirtualDevice] definition in [TestOptions.devices] to a functional
 * Android Virtual Device to be used with the emulator. This includes the downloading of required
 * system images, configuring the AVD, and creating an AVD snapshot.
 *
 * This task is required as a dependency for all Unified Testing Platform Tasks that require this
 * device.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class ManagedDeviceInstrumentationTestSetupTask: NonIncrementalGlobalTask() {

    @get: Internal
    abstract val sdkService: Property<SdkComponentsBuildService>

    @get: Internal
    abstract val avdService: Property<AvdComponentsBuildService>

    @get: Input
    abstract val compileSdkVersion: Property<String>

    @get: Input
    abstract val buildToolsRevision: Property<Revision>

    @get: Input
    abstract val abi: Property<String>

    @get: Input
    abstract val apiLevel: Property<Int>

    @get: Input
    abstract val systemImageVendor: Property<String>

    @get: Input
    abstract val hardwareProfile: Property<String>

    @get: Input
    abstract val emulatorGpuFlag: Property<String>

    // Used in error messaging
    @get: Internal
    abstract val managedDeviceName: Property<String>

    // Used in error messaging.
    @get: Internal
    abstract val require64Bit: Property<Boolean>

    override fun doTaskAction() {
        assertNoWearTvOrAuto()

        workerExecutor.noIsolation().submit(ManagedDeviceSetupRunnable::class.java) {
            it.initializeWith(projectPath,  path, analyticsService)
            it.sdkService.set(sdkService)
            it.compileSdkVersion.set(compileSdkVersion)
            it.buildToolsRevision.set(buildToolsRevision)
            it.avdService.set(avdService)
            it.deviceName.set(
                computeAvdName(
                    apiLevel.get(), systemImageVendor.get(), abi.get(), hardwareProfile.get()))
            it.hardwareProfile.set(hardwareProfile)
            it.emulatorGpuFlag.set(emulatorGpuFlag)
            it.managedDeviceName.set(managedDeviceName)
            it.systemImageVendor.set(systemImageVendor)
            it.apiLevel.set(apiLevel)
            it.require64Bit.set(require64Bit)
            it.abi.set(abi)
        }
    }

    private fun assertNoWearTvOrAuto() {
        // Since we presently don't support wear and tv devices, we need to check
        // if the developer is trying to use an image from those sources.
        if (isWearTvOrAutoSource(systemImageVendor.get())) {
            error(
                """
                    ${managedDeviceName.get()} has a systemImageSource of ${systemImageVendor.get()}.
                    Wear, TV and Auto devices are presently not supported with Gradle Managed Devices.
                """.trimIndent()
            )
        }

        // Or is attempting to use a wear, tv, or automotive device profile.
        if (isWearTvOrAutoDevice(hardwareProfile.get())) {

            error(
                """
                    ${managedDeviceName.get()} has a device profile of ${hardwareProfile.get()}.
                    Wear, TV and Auto devices are presently not supported with Gradle Managed Devices.
                """.trimIndent()
            )
        }
    }

    abstract class ManagedDeviceSetupRunnable : ProfileAwareWorkAction<ManagedDeviceSetupParams>() {
        override fun run() {
            val versionedSdkLoader = parameters.sdkService.get().sdkLoader(
                compileSdkVersion = parameters.compileSdkVersion,
                buildToolsRevision = parameters.buildToolsRevision
            )
            val imageHash = computeImageHash()
            val sdkImageProvider = versionedSdkLoader.sdkImageDirectoryProvider(imageHash)
            if (!sdkImageProvider.isPresent) {
                error(generateSystemImageErrorMessage(
                    parameters.managedDeviceName.get(),
                    parameters.apiLevel.get(),
                    parameters.systemImageVendor.get(),
                    parameters.require64Bit.get(),
                    versionedSdkLoader))
            }
            parameters.avdService.get().avdProvider(
                sdkImageProvider,
                imageHash,
                parameters.deviceName.get(),
                parameters.hardwareProfile.get()).get()

            parameters.avdService.get().ensureLoadableSnapshot(
                parameters.deviceName.get(),
                parameters.emulatorGpuFlag.get())
        }

        private fun computeImageHash(): String =
            computeSystemImageHashFromDsl(
                parameters.apiLevel.get(),
                parameters.systemImageVendor.get(),
                parameters.abi.get())
    }

    abstract class ManagedDeviceSetupParams : ProfileAwareWorkAction.Parameters() {
        abstract val sdkService: Property<SdkComponentsBuildService>
        abstract val compileSdkVersion: Property<String>
        abstract val buildToolsRevision: Property<Revision>
        abstract val avdService: Property<AvdComponentsBuildService>
        abstract val deviceName: Property<String>
        abstract val hardwareProfile: Property<String>
        abstract val emulatorGpuFlag: Property<String>
        abstract val managedDeviceName: Property<String>
        abstract val systemImageVendor: Property<String>
        abstract val apiLevel: Property<Int>
        abstract val require64Bit: Property<Boolean>
        abstract val abi: Property<String>
    }

    class CreationAction(
        override val name: String,
        private val systemImageSource: String,
        private val apiLevel: Int,
        private val abi: String,
        private val hardwareProfile: String,
        private val managedDeviceName: String,
        private val require64Bit: Boolean,
        creationConfig: GlobalTaskCreationConfig
    ) : GlobalTaskCreationAction<ManagedDeviceInstrumentationTestSetupTask>(creationConfig) {

        constructor(
            name: String,
            managedDevice: ManagedVirtualDevice,
            creationConfig: GlobalTaskCreationConfig
        ): this(
            name,
            managedDevice.systemImageSource,
            managedDevice.apiLevel,
            computeAbiFromArchitecture(managedDevice),
            managedDevice.device,
            managedDevice.name,
            managedDevice.require64Bit,
            creationConfig)

        override val type: Class<ManagedDeviceInstrumentationTestSetupTask>
            get() = ManagedDeviceInstrumentationTestSetupTask::class.java

        override fun configure(task: ManagedDeviceInstrumentationTestSetupTask) {
            super.configure(task)
            task.sdkService.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
            task.compileSdkVersion.setDisallowChanges(creationConfig.compileSdkHashString)
            task.buildToolsRevision.setDisallowChanges(creationConfig.buildToolsRevision)
            task.avdService.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )

            task.systemImageVendor.setDisallowChanges(systemImageSource)
            task.apiLevel.setDisallowChanges(apiLevel)
            task.abi.setDisallowChanges(abi)
            task.hardwareProfile.setDisallowChanges(hardwareProfile)

            task.emulatorGpuFlag.setDisallowChanges(
                computeManagedDeviceEmulatorMode(creationConfig.services.projectOptions)
            )

            task.managedDeviceName.setDisallowChanges(managedDeviceName)
            task.require64Bit.setDisallowChanges(require64Bit)
        }
    }

    companion object {
        @VisibleForTesting
        fun generateSystemImageErrorMessage(
            deviceName: String,
            apiLevel: Int,
            systemImageSource: String,
            require64Bit: Boolean,
            versionedSdkLoader: VersionedSdkLoader
        ) : String {
            // If the system image wasn't available. Check to see if we are offline.
            if (versionedSdkLoader.offlineMode) {
                return """
                    The system image for $deviceName is not available and Gradle is in offline mode.
                    Could not download the image or find other compatible images.
                """.trimIndent()
            }

            val allImages = versionedSdkLoader.allSystemImageHashes() ?: listOf()

            return ManagedDeviceImageSuggestionGenerator(
                osArchitecture,
                deviceName,
                apiLevel,
                systemImageSource,
                require64Bit,
                allImages
            ).message
        }
    }
}
