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

package com.android.build.gradle.internal.services

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.services.AndroidLocationsBuildService.AndroidLocations
import com.android.build.gradle.internal.utils.EnvironmentProviderImpl
import com.android.build.gradle.internal.utils.GradleEnvironmentProviderImpl
import com.android.prefs.AbstractAndroidLocations
import com.android.prefs.AndroidLocationsProvider
import com.android.utils.ILogger
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.absolutePathString

/**
 * A build service around [AndroidLocations] in order to make this basically a singleton
 * while not using static fields.
 *
 * Using static fields (like we used to do) means that the cached value is tied to the loaded
 * class and can survive across different builds, even if the computed value would have changed
 * (due to injected environment value for instance), as long as the gradle daemon is re-used.
 */
abstract class AndroidLocationsBuildService @Inject constructor(
    providerFactory: ProviderFactory
) : BuildService<BuildServiceParameters.None>, AutoCloseable, AndroidLocationsProvider {

     // ----- AndroidLocationsProvider -----

    override val prefsLocation: Path
        get() = androidLocations.prefsLocation

    override val avdLocation: Path
        get() = androidLocations.avdLocation

    override val gradleAvdLocation: Path
        get() = androidLocations.gradleAvdLocation

    override val userHomeLocation: Path
        get() = androidLocations.userHomeLocation

    // -----

    override fun close() {
        // nothing to be done here
    }

    /**
     * Use [ConfigPhaseFileCreator] to create android folder during configuration phase to avoid
     * configuration cache miss.
     */
    private val androidLocations = AndroidLocations(
        EnvironmentProviderImpl(GradleEnvironmentProviderImpl(providerFactory)),
        LoggerWrapper(Logging.getLogger("AndroidLocations"))
    ).also { androidLocations ->
        providerFactory.of(AndroidDirectoryCreator::class.java) {
            it.parameters.androidDir.set(File(androidLocations.computeAndroidFolder().absolutePathString()))
        }.get()
    }

    abstract class AndroidDirectoryCreator :
            ConfigPhaseFileCreator<String, AndroidDirectoryCreator.Params> {
        interface Params : ConfigPhaseFileCreator.Params {
            val androidDir: DirectoryProperty
        }

        override fun obtain(): String {
            parameters.androidDir.get().asFile.mkdirs()
            return IGNORE_FILE_CREATION
        }
    }

    class RegistrationAction(
        project: Project
    ) : ServiceRegistrationAction<AndroidLocationsBuildService, BuildServiceParameters.None>(
        project,
        AndroidLocationsBuildService::class.java
    ) {
        override fun configure(parameters: BuildServiceParameters.None) {
        }
    }

    /**
     * Implementation of [AbstractAndroidLocations] for usage inside the build services
     */
    private class AndroidLocations(
        environmentProvider: EnvironmentProviderImpl,
        logger: ILogger
    ): AbstractAndroidLocations(environmentProvider, logger, silent = true)
}
