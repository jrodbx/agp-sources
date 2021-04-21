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

package com.android.prefs

import com.android.io.CancellableFileIo
import com.android.utils.EnvironmentProvider
import com.android.utils.ILogger
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A class that computes various locations used by the Android tools.
 *
 * The values are computed once and then memoized inside the instance.
 *
 * To use as a singleton, use [AndroidLocationsSingleton].
 */
abstract class AbstractAndroidLocations protected constructor(
    private val environmentProvider: EnvironmentProvider,
    private val logger: ILogger,
    private val silent: Boolean = true
): AndroidLocationsProvider {

    companion object {
        /**
         * The name of the .android folder returned by [prefsLocation].
         */
        @JvmField
        val FOLDER_DOT_ANDROID = ".android"

        /**
         * Virtual Device folder inside the path returned by [avdLocation]
         */
        @JvmField
        val FOLDER_AVD = "avd"

        @JvmField
        val ANDROID_PREFS_ROOT = "ANDROID_PREFS_ROOT"
    }

    /**
     * Computes, memoizes in the instance, and returns the location of the .android folder
     *
     * To query the AVD Folder, use [avdLocation] as it could be be overridden
     */
    @get:Throws(AndroidLocationsException::class)
    override val prefsLocation: Path by lazy {
        rootLocation.resolve(FOLDER_DOT_ANDROID).also {
            if (CancellableFileIo.notExists(it)) {
                try {
                    Files.createDirectories(it)
                } catch (e: SecurityException) {
                    throw AndroidLocationsException(
                        """Unable to create folder '$it'.
|This is the path of preference folder expected by the Android tools.""",
                        e
                    )
                }
            } else if (CancellableFileIo.isRegularFile(it)) {
                throw AndroidLocationsException(
                    """$it is not a directory!
This is the path of preference folder expected by the Android tools."""
                )
            }
        }
    }

    /**
     * Computes, memoizes in the instance, and returns the location of the AVD folder.
     */
    @get:Throws(AndroidLocationsException::class)
    override val avdLocation: Path by lazy {
        // check if the location is overridden, if not use default
        findValidPath(mutableListOf(), Global.ANDROID_AVD_HOME) ?: prefsLocation.resolve(FOLDER_AVD)
    }

    /**
     * Computes, memoizes in the instance, and returns the location of the home folder.
     */
    override val userHomeLocation: Path? by lazy {
        findValidPath(mutableListOf(), Global.TEST_TMPDIR, Global.USER_HOME, Global.HOME)
    }

    /**
     * Computes, memoizes in the instance, and returns the root folders where the android folder
     * will be located
     *
     * This is NOT the .android folder. Use [prefsLocation]
     * To query the AVD Folder, use [avdLocation] as it could be overridden
     */
    private val rootLocation: Path by lazy {
        val visitedVariables = mutableListOf<Pair<String, String>>()
        findValidPath(
            visitedVariables,
            Global.ANDROID_PREFS_ROOT,
            Global.TEST_TMPDIR,
            Global.USER_HOME,
            Global.HOME
        ) ?: throw AndroidLocationsException.createForPrefsRoot(visitedVariables)
    }

    /**
     * Checks a list of system properties and/or system environment variables for validity, and
     * returns the first one.
     *
     * @param environmentProvider Source for getting the system properties/env variables.
     * @param globalVars The variables to check. Order does matter.
     * @return the content of the first property/variable that is a valid directory.
     */
    private fun findValidPath(
        accumulator: MutableList<Pair<String, String>>,
        vararg globalVars: Global
    ): Path? {
        for (globalVar in globalVars) {
            val path = validatePath(globalVar, accumulator)
            if (path != null) {
                return path
            }
        }
        return null
    }

    private fun validatePath(
        globalVar: Global,
        accumulator: MutableList<Pair<String, String>>
    ): Path? {

        val sysPropPath = if (globalVar.isSysProp) {
            checkPath(
                globalVar,
                environmentProvider::getSystemProperty,
                "system property",
                accumulator
            )
        } else null

        return sysPropPath
            ?: if (globalVar.isEnvVar) {
                checkPath(
                    globalVar,
                    environmentProvider::getEnvVariable,
                    "environment variable",
                    accumulator
                )
            } else null
    }

    private fun checkPath(
        globalVar: Global,
        queryFunction: (String) -> String?,
        varTypeName: String,
        accumulator: MutableList<Pair<String, String>>
    ): Path? {
        var path = queryFunction(globalVar.propName)

        path?.let {
            accumulator.add("${globalVar.propName}($varTypeName)" to it)
        }

        if (globalVar == Global.ANDROID_PREFS_ROOT) {
            // Special Handling:
            // If the query is ANDROID_PREFS_ROOT, then also query ANDROID_SDK_HOME and compare
            // the values. If both values are set, they must match
            val androidSdkHomePath = queryFunction("ANDROID_SDK_HOME")
            if (path == null) {
                if (androidSdkHomePath != null) {
                    accumulator.add("ANDROID_SDK_HOME($varTypeName)" to androidSdkHomePath)
                    path = validateAndroidSdkHomeValue(androidSdkHomePath)
                } else {
                    // both are null, return
                    return null
                }
            } else { // path != null
                if (androidSdkHomePath != null) {
                    accumulator.add("ANDROID_SDK_HOME($varTypeName)" to androidSdkHomePath)
                    if (path != androidSdkHomePath) {
                        throw AndroidLocationsException(
                            """
Both ANDROID_PREFS_ROOT and ANDROID_SDK_HOME are set to different values
Support for ANDROID_SDK_HOME is deprecated. Use ANDROID_PREFS_ROOT only.
Current values:
ANDROID_SDK_ROOT: $path
ANDROID_SDK_HOME: $androidSdkHomePath""".trimIndent()
                        )
                    }
                }
            }
        }

        if (path == null) {
            return null
        }

        return Paths.get(path).asExistingDirectory()
    }

    private fun validateAndroidSdkHomeValue(path: String): String? {
        val file = Paths.get(path).asExistingDirectory() ?: return null

        if (isSdkRootWithoutDotAndroid(file)) {
            val message =
                """ANDROID_SDK_HOME is set to the root of your SDK: $path
ANDROID_SDK_HOME is meant to be the path of the preference folder expected by the Android tools.
It should NOT be set to the same as the root of your SDK.
To set a custom SDK Location, use ANDROID_SDK_ROOT.
If this is not set we default to: ${
                    findValidPath(
                        mutableListOf(),
                        Global.TEST_TMPDIR,
                        Global.USER_HOME,
                        Global.HOME
                    )
                }"""


            if (silent) {
                logger.warning(message)
            } else {
                throw AndroidLocationsException(message)
            }
        }

        return path
    }

    private fun isSdkRootWithoutDotAndroid(folder: Path): Boolean {
        return (folder.hasSubFolder("platforms") &&
                folder.hasSubFolder("platform-tools") &&
                !folder.hasSubFolder(FOLDER_DOT_ANDROID))
    }

    private fun Path.hasSubFolder(subFolder: String): Boolean {
        return CancellableFileIo.isDirectory(resolve(subFolder))
    }

    private fun Path.asExistingDirectory(): Path? {
        return if (CancellableFileIo.isDirectory(this)) this else null
    }
}

/**
 * Enum describing which variables to check and whether they should be checked via
 * [EnvironmentProvider.getSystemProperty] or [EnvironmentProvider.getEnvVariable] or both.
 */
private enum class Global(
    val propName: String,
    val isSysProp: Boolean,
    val isEnvVar: Boolean
) {

    ANDROID_AVD_HOME(
        propName = "ANDROID_AVD_HOME",
        isSysProp = true,
        isEnvVar = true
    ),
    ANDROID_PREFS_ROOT(
        propName = AbstractAndroidLocations.ANDROID_PREFS_ROOT,
        isSysProp = true,
        isEnvVar = true
    ),
    TEST_TMPDIR(  // Bazel kludge
        propName = "TEST_TMPDIR",
        isSysProp = false,
        isEnvVar = true
    ),
    USER_HOME(
        propName = "user.home",
        isSysProp = true,
        isEnvVar = false
    ),
    HOME(
        propName = "HOME",
        isSysProp = false,
        isEnvVar = true
    );
}
