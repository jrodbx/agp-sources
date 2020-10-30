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

package com.android.build.gradle.internal.profile

import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.builder.profile.ChromeTracingProfileConverter
import com.android.builder.profile.ProcessProfileWriter
import com.android.utils.PathUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.IOException
import java.nio.file.Path

/**
 * A build service used to handle finalization of the profile report. Previously, this was
 * accomplished by using a build finished listener, but this is not compatible with configuration
 * caching.
 *
 * This is a temporary solution until analytics is implemented in a configuration caching friendly
 * way, b/157470515.
 */
abstract class ProfileCleanupBuildService : BuildService<ProfileCleanupBuildService.Parameters>,
    AutoCloseable {

    interface Parameters : BuildServiceParameters {
        val profileDir: Property<String>
        val enableJsonProfile: Property<Boolean>
    }

    override fun close() {
        ProfilerInitializer.unregister(profileDir)
    }

    private var profileDir: Path? = null

    fun projectEvaluated(gradle: Gradle) {
        gradle.allprojects { collectProjectInfo(it) }
        if (parameters.profileDir.orNull != null) {
            profileDir = gradle.rootProject.file(parameters.profileDir.get()).toPath()
        } else if (parameters.enableJsonProfile.get() == true) {
            // If profile json is enabled but no directory is given for the profile outputs, default to build/android-profile
            profileDir = gradle.rootProject.buildDir.toPath()
                .resolve(ProfilerInitializer.PROFILE_DIRECTORY)
        }
        if (profileDir != null) {
            // Proactively delete the folder containing extra chrome traces to be merged.
            val extraChromeTracePath = profileDir!!.resolve(
                ChromeTracingProfileConverter.EXTRA_CHROME_TRACE_DIRECTORY
            )
            try {
                PathUtils.deleteRecursivelyIfExists(extraChromeTracePath)
            } catch (e: IOException) {
                Logging.getLogger(ProfileCleanupBuildService::class.java).warn(
                    "Cannot extra Chrome trace directory $extraChromeTracePath. The generated" +
                            "Chrome trace file may contain stale data.",
                    e
                )
            }
        }
    }

    private fun collectProjectInfo(project: Project) {
        val analyticsProject = ProcessProfileWriter.getProject(project.path)
        project.plugins.all { plugin: Plugin<*> ->
            analyticsProject.addPlugin(
                AnalyticsUtil.toProto(
                    plugin
                )
            )
        }
    }

    class RegistrationAction(
        project: Project,
        private val profileDir: String?,
        private val enableJsonProfile: Boolean
    ) :
        ServiceRegistrationAction<ProfileCleanupBuildService, Parameters>(
            project,
            ProfileCleanupBuildService::class.java
        ) {
        override fun configure(parameters: Parameters) {
            parameters.profileDir.set(profileDir)
            parameters.enableJsonProfile.set(enableJsonProfile)
        }
    }
}