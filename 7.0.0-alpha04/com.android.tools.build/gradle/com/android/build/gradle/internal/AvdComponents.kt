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

import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.sdklib.repository.AndroidSdkHandler
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import javax.inject.Inject

/**
 * Build Service for loading and creating Android Virtual Devices.
 */
abstract class AvdComponentsBuildService @Inject constructor(
    private val objectFactory: ObjectFactory,
    private val providerFactory: ProviderFactory
) :
        BuildService<AvdComponentsBuildService.Parameters> {

    interface Parameters : BuildServiceParameters {
        val sdkService: Property<SdkComponentsBuildService>
        val avdLocation: DirectoryProperty
    }

    private val avdManager: AvdManager by lazy {
        AvdManager(
            parameters.avdLocation.get().asFile,
            parameters.sdkService.get(),
            AndroidSdkHandler.getInstance(
                parameters.sdkService.get().sdkDirectoryProvider.get().asFile.toPath()
            ))
    }

    /**
     * Returns the location of the shared avd folder.
     */
    val avdFolder: Provider<Directory> = parameters.avdLocation

    /**
     * Returns the location of the emulator.
     */
    val emulatorDirectory: Provider<Directory> =
        parameters.sdkService.get().emulatorDirectoryProvider

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
     */
    fun deleteAvds(avds: List<String>) {
        avdManager.deleteAvds(avds)
    }

    fun avdProvider(
        imageHash: String,
        deviceName: String,
        hardwareProfile: String
    ): Provider<Directory> =
        objectFactory.directoryProperty().fileProvider(providerFactory.provider {
            avdManager.createOrRetrieveAvd(imageHash, deviceName, hardwareProfile)
        })

    fun deviceSnapshotCreated(deviceName: String): Boolean =
        avdManager.deviceSnapshotCreated(deviceName)

    class RegistrationAction(
        project: Project,
        private val avdFolderLocation: Provider<Directory>,
        private val sdkService: Provider<SdkComponentsBuildService>
    ) : ServiceRegistrationAction<AvdComponentsBuildService, Parameters>(
        project,
        AvdComponentsBuildService::class.java
    ) {

        override fun configure(parameters: Parameters) {
            parameters.avdLocation.set(avdFolderLocation)
            parameters.sdkService.set(sdkService)
        }
    }

}
