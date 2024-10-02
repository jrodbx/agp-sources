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

package com.android.build.gradle.internal

import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.testing.AdbHelper
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.ProjectOptions
import com.android.repository.Revision
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.utils.ILogger
import com.android.utils.PathUtils
import javax.inject.Inject
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

// TODO(b/233249957): find a way to compute the default based on resources.
private const val DEFAULT_MAX_GMDS = 4
private const val SECONDS_PER_MINUTE = 60L

/**
 * Build Service for loading and creating Android Virtual Devices.
 */
abstract class AvdComponentsBuildService @Inject constructor(
    private val objectFactory: ObjectFactory,
    private val providerFactory: ProviderFactory
) :
        BuildService<AvdComponentsBuildService.Parameters> {

    private val logger: ILogger = LoggerWrapper.getLogger(AvdComponentsBuildService::class.java)

    interface Parameters : BuildServiceParameters {
        val sdkService: Property<SdkComponentsBuildService>
        val compileSdkVersion: Property<String>
        val buildToolsRevision: Property<Revision>
        val androidLocationsService: Property<AndroidLocationsBuildService>
        val avdLocation: DirectoryProperty
        val showEmulatorKernelLogging: Property<Boolean>
        val deviceSetupTimeoutMinutes: Property<Int>
        val maxConcurrentDevices: Property<Int>
    }

    private val avdManager: AvdManager by lazy {
        val locationsService = parameters.androidLocationsService.get()
        val versionedSdkLoader = parameters.sdkService.map {
            it.sdkLoader(parameters.compileSdkVersion, parameters.buildToolsRevision)
        }
        val adbHelper = AdbHelper(versionedSdkLoader)
        val snapshotTimeoutSecs = if (parameters.deviceSetupTimeoutMinutes.isPresent()) {
            parameters.deviceSetupTimeoutMinutes.get() * SECONDS_PER_MINUTE
        } else {
            null
        }
        AvdManager(
            parameters.avdLocation.get().asFile,
            versionedSdkLoader,
            AndroidSdkHandler.getInstance(
                locationsService,
                parameters.sdkService.get().sdkDirectoryProvider.get().asFile.toPath()
            ),
            locationsService,
            AvdSnapshotHandler(
                parameters.showEmulatorKernelLogging.get(),
                snapshotTimeoutSecs,
                adbHelper
            ),
            ManagedVirtualDeviceLockManager(
                locationsService,
                parameters.maxConcurrentDevices.getOrElse(DEFAULT_MAX_GMDS)
            ),
            adbHelper
        )
    }

    val lockManager: ManagedVirtualDeviceLockManager = avdManager.deviceLockManager

    /**
     * Returns the location of the shared avd folder.
     */
    val avdFolder: Provider<Directory> = parameters.avdLocation

    /**
     * Returns the location of the emulator.
     */
    val emulatorDirectory: Provider<Directory> =
        parameters.sdkService.flatMap {
            it.sdkLoader(
                parameters.compileSdkVersion,
                parameters.buildToolsRevision).emulatorDirectoryProvider
        }

    /**
     * Returns the names of all avds currently in the shared avd folder.
     */
    fun allAvds(): List<String> = avdManager.allAvds()

    /**
     * Removes all the specified avds.
     *
     * This will delete the specified avds from the shared avd folder and update the avd cache.
     *
     * @param avds names of the avds to be deleted.
     * @return the avds that were deleted.
     */
    fun deleteAvds(avds: List<String>): List<String> =
        avdManager.deleteAvds(avds)

    /**
     * Removes the legacy Gradle Managed Device Avd directory (.android/gradle/avd), which had
     * been used until 7.3.0-alpha08.
     */
    fun deleteLegacyGradleManagedDeviceAvdDirectory() {
        PathUtils.deleteRecursivelyIfExists(
            parameters.androidLocationsService.get().prefsLocation.resolve("gradle").resolve("avd"))
    }

    fun avdProvider(
        imageProvider: Provider<Directory>,
        imageHash: String,
        deviceName: String,
        hardwareProfile: String
    ): Provider<Directory> =
        objectFactory.directoryProperty().fileProvider(providerFactory.provider {
            avdManager.createOrRetrieveAvd(imageProvider, imageHash, deviceName, hardwareProfile)
        })

    /**
     * Ensures that a given AVD has a loadable snapshot for the current emulator version.
     *
     * Checks to make sure the default_boot snapshot on a given avd is loadable with the current
     * emulator version. If not, a new snapshot is created which will replace any old snapshot if
     * it existed.
     *
     * If a snapshot fails to be created, an error is thrown.
     *
     * @param deviceName The name of the avd to check. This avd should have already been created via
     * a call to get() on the provider returned by [avdProvider].
     */
    fun ensureLoadableSnapshot(deviceName: String, emulatorGpuMode: String) {
        avdManager.loadSnapshotIfNeeded(deviceName, emulatorGpuMode)
    }

    /** Closes all active emulators having an id with the given prefix. This should be used to close
     * emulators that may remain after a crashed UTP test run.
     *
     * @param idPrefix the prefix that is looke for to close the active emulators. All emulators
     * that have an id not starting with this prefix are ignored.
     */
    fun closeOpenEmulators(idPrefix: String) {
        avdManager.closeOpenEmulators(idPrefix)
    }

    class RegistrationAction(
        project: Project,
        private val projectOptions: ProjectOptions,
        private val avdFolderLocation: Provider<Directory>,
        private val sdkService: Provider<SdkComponentsBuildService>,
        private val compileSdkVersion: Provider<String>,
        private val buildToolsRevision: Provider<Revision>
    ) : ServiceRegistrationAction<AvdComponentsBuildService, Parameters>(
        project,
        AvdComponentsBuildService::class.java
    ) {

        override fun configure(parameters: Parameters) {
            parameters.avdLocation.set(avdFolderLocation)
            parameters.sdkService.set(sdkService)
            parameters.compileSdkVersion.set(compileSdkVersion)
            parameters.buildToolsRevision.set(buildToolsRevision)
            parameters.sdkService.set(getBuildService(project.gradle.sharedServices))
            parameters.androidLocationsService.set(getBuildService(project.gradle.sharedServices))
            parameters.showEmulatorKernelLogging.set(
                projectOptions[BooleanOption.GRADLE_MANAGED_DEVICE_EMULATOR_SHOW_KERNEL_LOGGING])
            parameters.deviceSetupTimeoutMinutes.set(
                projectOptions[IntegerOption.GRADLE_MANAGED_DEVICE_SETUP_TIMEOUT_MINUTES]
            )
            parameters.maxConcurrentDevices.set(
                projectOptions[IntegerOption.GRADLE_MANAGED_DEVICE_MAX_CONCURRENT_DEVICES]
            )
        }
    }

}
