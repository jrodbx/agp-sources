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
import com.android.repository.Revision
import com.android.build.gradle.internal.services.getBuildService
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.utils.ILogger
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

    private val logger: ILogger = LoggerWrapper.getLogger(AvdComponentsBuildService::class.java)

    interface Parameters : BuildServiceParameters {
        val sdkService: Property<SdkComponentsBuildService>
        val compileSdkVersion: Property<String>
        val buildToolsRevision: Property<Revision>
        val androidLocationsService: Property<AndroidLocationsBuildService>
        val avdLocation: DirectoryProperty
    }

    private val avdManager: AvdManager by lazy {
        val locationsService = parameters.androidLocationsService.get()
        AvdManager(
            parameters.avdLocation.get().asFile,
            parameters.sdkService.map {
                it.sdkLoader(parameters.compileSdkVersion, parameters.buildToolsRevision)
            },
            AndroidSdkHandler.getInstance(
                locationsService,
                parameters.sdkService.get().sdkDirectoryProvider.get().asFile.toPath()
            ),
            locationsService,
            AvdSnapshotHandler()
        )
    }

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
     */
    fun deleteAvds(avds: List<String>) {
        avdManager.deleteAvds(avds)
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
    fun ensureLoadableSnapshot(deviceName: String) {
        avdManager.loadSnapshotIfNeeded(deviceName)
    }

    class RegistrationAction(
        project: Project,
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
        }
    }

}
