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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.cxx.logging.ThreadLoggingEnvironment
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.ModelField
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_HAS_BUILD_TIME_INFORMATION
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.utils.cxx.os.exe
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_MODULE_CMAKE_EXECUTABLE
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_MODULE_NINJA_EXECUTABLE
import com.android.build.gradle.internal.cxx.settings.rewrite
import com.android.build.gradle.tasks.NativeBuildSystem.NDK_BUILD
import java.io.ByteArrayOutputStream

/**
 * Build service that provides locations for C/C++ tools that aren't in the NDK (cmake and ninja).
 * Results are cached within the BuildService so that discovery of these tools is shared between
 * different tasks.
 *
 * The scope of sharing is per-build. For this reason, we are making assumptions like cmake.exe
 * and ninja.exe aren't modified during the build.
 */
abstract class NativeLocationsBuildService @Inject constructor(private val exec: ExecOperations)
    : BuildService<NativeLocationsBuildService.ServiceParameters> {

    private val androidLocationProvider get() = parameters.androidLocationsService.get()
    private val sdkComponents get() = parameters.sdkService.get()
    private val cmakeLocations = mutableMapOf<CMakeLocatorParameters, File?>()
    private val ninjaLocations = mutableMapOf<NinjaLocatorParameters, File?>()
    private val toolVersions = mutableMapOf<File, String>()

    /**
     * Locate CMake.exe and cache the result.
     * If not found, try to install CMake from the SDK.
     */
    fun locateCMake(
        cmakeVersionFromDsl: String?,
        localPropertiesCMakeDir: File?
    ) : File? {
        ThreadLoggingEnvironment.requireExplicitLogger()
        synchronized(cmakeLocations) {
            return cmakeLocations.computeIfAbsent(CMakeLocatorParameters(
                cmakeVersionFromDsl = cmakeVersionFromDsl,
                localPropertiesCMakeDir = localPropertiesCMakeDir)) {
                CmakeLocator().findCmakePath(
                    cmakeVersionFromDsl = cmakeVersionFromDsl,
                    localPropertiesCMakeDir = localPropertiesCMakeDir,
                    androidLocationsProvider = androidLocationProvider,
                    sdkFolder = sdkComponents.sdkDirectoryProvider.get().asFile,
                    versionExecutor = ::versionOf
                ) { version -> sdkComponents.installCmake(version) }
                    ?.resolve("bin/cmake$exe")
            }
        }
    }

    /**
     * Locate ninja.exe and cache the result.
     */
    fun locateNinja(cmakeBinFolder: File?) : File? {
        ThreadLoggingEnvironment.requireExplicitLogger()
        synchronized(ninjaLocations) {
            return ninjaLocations.computeIfAbsent(NinjaLocatorParameters(
                cmakeBinFolder = cmakeBinFolder)) {
                    NinjaLocator().findNinjaPath(
                        cmakePath = cmakeBinFolder,
                        sdkFolder = sdkComponents.sdkDirectoryProvider.get().asFile
                    )
            }
        }
    }

    /**
     * Execute given tool with "--version" argument and return stdout as a string.
     */
    private fun versionOf(tool : File) : String {
        return toolVersions.computeIfAbsent(tool) {
            val stdout = ByteArrayOutputStream()
            exec.exec { spec ->
                spec.commandLine("$tool", "--version")
                spec.standardOutput = stdout
            }
            "$stdout"
        }
    }

    /**
     * Called to register this service with Gradle.
     */
    private class RegistrationAction(
        project: Project,
    ) : ServiceRegistrationAction<NativeLocationsBuildService, ServiceParameters>(
        project,
        NativeLocationsBuildService::class.java
    ) {
        override fun configure(parameters: ServiceParameters) {
            parameters.sdkService.set(getBuildService(project.gradle.sharedServices))
            parameters.androidLocationsService.set(getBuildService(project.gradle.sharedServices))
        }
    }

    /**
     * Parameters to this service.
     */
    interface ServiceParameters : BuildServiceParameters {
        val sdkService: Property<SdkComponentsBuildService>
        val androidLocationsService: Property<AndroidLocationsBuildService>
    }

    /**
     * Parameters for caching location of cmake.exe.
     */
    private data class CMakeLocatorParameters(
        val cmakeVersionFromDsl: String?,
        val localPropertiesCMakeDir: File?
    )

    /**
     * Parameters for caching location of ninja.exe.
     */
    private data class NinjaLocatorParameters(
        val cmakeBinFolder: File?
    )

    companion object {
        /**
         * Register this service. It is safe to call multiple times and will only be registered
         * once per build (like all build services).
         */
        fun register(project : Project) {
            RegistrationAction(project).execute().get()
        }
    }
}

/**
 * Given a location service, update the CxxAbiModel to contain the true paths to CMake and Ninja.
 */
fun CxxAbiModel.rewriteWithLocations(locationService : NativeLocationsBuildService) : CxxAbiModel {
    if (variant.module.hasBuildTimeInformation) error("Already updated this ABI with build time information")
    if (variant.module.buildSystem == NDK_BUILD) {
        // No work to do, just set the hasBuildTimeInformation flag.
        return copy(
            variant = variant.copy(
                module = variant.module.copy(
                    hasBuildTimeInformation = true
                )
            )
        )
    }
    val cmake = variant.module.cmake
    val cmakeExe = locationService.locateCMake(
        cmakeVersionFromDsl = cmake?.cmakeVersionFromDsl,
        localPropertiesCMakeDir = cmake?.cmakeDirFromPropertiesFile
    )
    val ninjaExe = locationService.locateNinja(
        cmakeBinFolder = cmakeExe?.parentFile
    )
    val cmakeExePath = cmakeExe?.path ?: ""
    val ninjaExePath = ninjaExe?.path ?: ""
    val buildTimeProperties = mapOf(
        NDK_MODULE_CMAKE_EXECUTABLE.configurationPlaceholder to cmakeExePath,
        NDK_MODULE_NINJA_EXECUTABLE.configurationPlaceholder to ninjaExePath,
    )
    return rewrite { property, value ->
        when(property) {
            CXX_MODULE_MODEL_HAS_BUILD_TIME_INFORMATION -> "true"
            else -> {
                var result = value
                for ((search, replace) in buildTimeProperties) {
                    result = result.replace(search, replace)
                }
                result
            }
        }
    }
}
