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
import com.android.build.gradle.internal.dsl.ManagedVirtualDevice
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal

private val DEVICE_PREFIX = "dev"
private val DEVICE_DEVIDER = "_"
private val SYSTEM_IMAGE_PREFIX = "system-images;"
private val HASH_DIVIDER = ";"

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
abstract class ManagedDeviceSetupTask: NonIncrementalTask() {

    @get: Internal
    abstract val avdService: Property<AvdComponentsBuildService>

    @get: Input
    abstract val abi: Property<String>

    @get: Input
    abstract val apiLevel: Property<Int>

    @get: Input
    abstract val systemImageVendor: Property<String>

    @get: Input
    abstract val hardwareProfile: Property<String>

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(ManagedDeviceSetupRunnable::class.java) {
            it.avdService.set(avdService)
            it.imageHash.set(computeImageHash())
            it.deviceName.set(computeDeviceName())
            it.hardwareProfile.set(hardwareProfile)
        }
    }

    abstract class ManagedDeviceSetupRunnable : ProfileAwareWorkAction<ManagedDeviceSetupParams>() {
        override fun run() {
            parameters.avdService.get().avdProvider(
                parameters.imageHash.get(),
                parameters.deviceName.get(),
                parameters.hardwareProfile.get()).get()

            // TODO(b/165626279) Expand the avd with the emulator command.
        }
    }

    abstract class ManagedDeviceSetupParams : ProfileAwareWorkAction.Parameters() {
        abstract val avdService: Property<AvdComponentsBuildService>
        abstract val imageHash: Property<String>
        abstract val deviceName: Property<String>
        abstract val hardwareProfile: Property<String>
    }

    private fun computeImageHash(): String {
        return SYSTEM_IMAGE_PREFIX +
                computeVersionString() + HASH_DIVIDER +
                computeVendorString() + HASH_DIVIDER +
                abi.get()
    }

    private fun computeDeviceName(): String {
        return DEVICE_PREFIX +
                apiLevel.get() + DEVICE_DEVIDER +
                systemImageVendor.get() + DEVICE_DEVIDER +
                abi.get() + DEVICE_DEVIDER +
                hardwareProfile.get().replace(' ', '_')
    }

    private fun computeVersionString() = "android-${apiLevel.get()}"

    private fun computeVendorString() = when (systemImageVendor.get()) {
        "google" -> "google_apis_playstore"
        "aosp" -> "default"
        else -> throw RuntimeException("Unrecognized systemImageVendor ${systemImageVendor.get()}" +
                ". \"google\" or \"aosp\" expected.")
    }

    class CreationAction(
        override val name: String,
        private val avdService: Provider<AvdComponentsBuildService>,
        private val systemImageSource: String,
        private val apiLevel: Int,
        private val abi: String,
        private val hardwareProfile: String
    ) : TaskCreationAction<ManagedDeviceSetupTask>() {

        constructor(
            name: String,
            avdService: Provider<AvdComponentsBuildService>,
            managedDevice: ManagedVirtualDevice
        ): this(
            name,
            avdService,
            managedDevice.systemImageSource,
            managedDevice.apiLevel,
            managedDevice.abi,
            managedDevice.device)

        override val type: Class<ManagedDeviceSetupTask>
            get() = ManagedDeviceSetupTask::class.java

        override fun configure(task: ManagedDeviceSetupTask) {
            task.avdService.setDisallowChanges(avdService)

            task.systemImageVendor.setDisallowChanges(systemImageSource)
            task.apiLevel.setDisallowChanges(apiLevel)
            task.abi.setDisallowChanges(abi)
            task.hardwareProfile.setDisallowChanges(hardwareProfile)
        }

    }
}
