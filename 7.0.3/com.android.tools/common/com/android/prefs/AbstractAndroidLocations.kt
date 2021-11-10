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
import com.android.prefs.AbstractAndroidLocations.Companion.FOLDER_DOT_ANDROID
import com.android.utils.EnvironmentProvider
import com.android.utils.ILogger
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
         * The name of the .android folder returned by [prefsLocation], unless [ANDROID_USER_HOME] is used
         */
        @JvmField
        val FOLDER_DOT_ANDROID = ".android"

        /**
         * Virtual Device folder inside the path returned by [avdLocation]
         */
        @JvmField
        val FOLDER_AVD = "avd"

        /**
         * Folder for the Android plugin for gradle, containing managed devices.
         */
        @JvmField
        val FOLDER_GRADLE = "gradle"

        /**
         * Virtual Device folder for devices managed by the Android plugin for gradle inside the
         * path returned by [gradleAvdLocation].
         */
        @JvmField
        val FOLDER_GRADLE_AVD = "avd"

        @JvmField
        @Deprecated("Use ANDROID_USER_HOME")
        val ANDROID_PREFS_ROOT = "ANDROID_PREFS_ROOT"

        @JvmStatic
        val ANDROID_USER_HOME = "ANDROID_USER_HOME"
    }

    /**
     * Computes, memoizes in the instance, and returns the location of the .android folder
     *
     * To query the AVD Folder, use [avdLocation] as it could be be overridden
     */
    @get:Throws(AndroidLocationsException::class)
    override val prefsLocation: Path by lazy {
        computeAndroidFolder().also {
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
        PathLocator(environmentProvider).singlePathOf(Global.ANDROID_AVD_HOME)
                ?: prefsLocation.resolve(FOLDER_AVD)
    }

    @get:Throws(AndroidLocationsException::class)
    override val gradleAvdLocation: Path by lazy {
        prefsLocation.resolve(FOLDER_GRADLE).resolve(FOLDER_GRADLE_AVD)
    }

    /**
     * Computes, memoizes in the instance, and returns the location of the home folder.
     */
    override val userHomeLocation: Path by lazy {
        val pathLocator = PathLocator(environmentProvider)
        pathLocator.firstPathOf(
            Global.TEST_TMPDIR,
            Global.USER_HOME,
            Global.HOME
        ) ?: throw AndroidLocationsException.createForHomeLocation(pathLocator.visitedVariables)
    }

    /**
     * Computes, and returns the root folder where the android folder
     * will be located.
     *
     * This is using the old, deprecated ANDROID_SDK_HOME or ANDROID_PREFS_ROOT as a backup in case
     * ANDROID_USER_HOME does not exist
     *
     */
    private fun computeAndroidFolder(): Path  {
        val locator = AndroidPathLocator(environmentProvider, if (!silent) logger else null)

        val folder =
                locator.singlePathOf(
                    Global.ANDROID_USER_HOME,
                    Global.ANDROID_PREFS_ROOT,
                    Global.ANDROID_SDK_HOME
                )

        if (folder != null) {
            if (locator.visitedVariables.size > 1) {
                // even through we get a regular path, we ended up visiting more than one value.
                // Show a warning
                if (!silent) {
                    val message = combineLocationValuesIntoMessage(
                        values = locator.visitedVariables,
                        prefix = """
                            More than one location points to the Android preference location
                            but only one is valid
                            """.trimIndent(),
                        modifier = { value ->
                            if (value.global.mustExist && !CancellableFileIo.isDirectory(value.path)) {
                                "does not exist"
                            } else null
                        }
                    )

                    logger.warning(message)
                }
            }
            return folder
        }

        // don't use userHomeLocation as we want to be able to get the list of queried
        // paths.
        // Worst case we query for these variables twice before both userHomeLocation and
        // prefsLocation are cached.
        val pathLocator = PathLocator(environmentProvider)
        return pathLocator.firstPathOf(
            Global.TEST_TMPDIR,
            Global.USER_HOME,
            Global.HOME
        )?.resolve(FOLDER_DOT_ANDROID)
                ?: throw AndroidLocationsException(
                    combineLocationValuesIntoMessage(
                        values = locator.visitedVariables + pathLocator.visitedVariables,
                        prefix = """
                        Unable to find the location for the android preferences.
                        The following locations have been checked, but they do not exist:
                        """.trimIndent()
                    )
                )
    }
}

internal enum class VariableType(val displayName: String) {
    SYS_PROP("system property"),
    ENV_VAR("environment variable")
}


private data class QueryResult(
    val global: Global,
    val type: VariableType,
    val path: Path
): LocationValue {

    override val propertyName: String
        get() = global.propName

    override val queryType: String
        get() = type.displayName

    override val value: String
        get() = path.toString()
}

private data class VariableValue(
    override val propertyName: String,
    val type: VariableType,
    val path: Path,
    val correctPath: Path
): LocationValue {

    override val queryType: String
        get() = type.displayName

    override val value: String
        get() = path.toString()

}

private open class PathLocator(
    private val environmentProvider: EnvironmentProvider,
) {
    val visitedVariables = mutableListOf<QueryResult>()

    fun reset() {
        visitedVariables.clear()
    }

    /**
     * Search for a path and return as soon as a path if found.
     *
     * This does not detect conflicts between variables (or conflicts within a single variable
     * used by both env var and sys prop)
     */
    fun firstPathOf(
        vararg globalVars: Global,
    ): Path? {
        for (globalVar in globalVars) {
            val path = singlePathOf(globalVar)
            if (path != null) {
                return path
            }
        }

        return null
    }

    /**
     * Gathers a single path from a list of variables.
     *
     * This method will check all variables and only succeed if a single variable contains a valid
     * value. This includes checking for both env var and sys prop where applicable.
     */
    fun singlePathOf(
        vararg globalVars: Global
    ): Path? {
        val values =  globalVars.asSequence()
            .map { it.gatherPaths() }
            .flatMap { it.asSequence() }
            .toList()

        // check that the values are the same.
        return when {
            values.isEmpty() -> null
            values.size == 1 -> values.single().correctPath
            else -> {
                // Build a map of (location, list<Value>)
                // use the correct path as we want to compare the final path
                val map = values.groupBy { it.correctPath }
                // single content, we are done, return the path
                if (map.size == 1) {
                    map.keys.single()
                } else {
                    // if not, fail
                    val message = combineLocationValuesIntoMessage(
                        values = values,
                        prefix = """
                            Several environment variables and/or system properties contain different paths to the Android Preferences folder.
                            Please correct and use only one way to inject the preference location.
                            """.trimIndent(),
                        suffix = "It is recommended to use ANDROID_USER_HOME as other methods are deprecated"
                    )

                    throw AndroidLocationsException(message)
                }
            }
        }
    }

    /**
     * Gather the path(s) from a variable, querying both env var and system property, if applicable.
     *
     * If the paths found does not exist, but should, the value will not be included in the result.
     *
     * @return a list of value, possibly empty.
     */
    private fun Global.gatherPaths(
    ): List<VariableValue> {

        val sysProp = if (isSysProp) {
            queryPath(this, VariableType.SYS_PROP)
        } else null

        val envVar = if (isEnvVar) {
            queryPath(this, VariableType.ENV_VAR)
        } else null

        return listOfNotNull(sysProp, envVar)
    }

    protected open fun handlePath(globalVar: Global, path: Path): Path? {
        return if (globalVar.mustExist && !CancellableFileIo.isDirectory(path)) {
            null
        } else {
            path
        }
    }

    /**
     * Query a path for a variable, given a specific query mechanism
     *
     * this also checks for existence (if applicable), and adds a leaf to the return (if applicable)
     */
    private fun queryPath(
        globalVar: Global,
        queryType: VariableType
    ): VariableValue? {
        val location = when (queryType) {
            VariableType.SYS_PROP -> environmentProvider.getSystemProperty(globalVar.propName)
            VariableType.ENV_VAR -> environmentProvider.getEnvVariable(globalVar.propName)
        } ?: return null

        val path = Paths.get(location)
        visitedVariables.add(QueryResult(globalVar, queryType, path))

        val correctPath = handlePath(globalVar, path) ?: return null

        return VariableValue(
            globalVar.propName,
            queryType,
            path,
            correctPath
        )
    }
}

private class AndroidPathLocator(
    environmentProvider: EnvironmentProvider,
    private val logger: ILogger? = null
) : PathLocator(
    environmentProvider
) {

    override fun handlePath(globalVar: Global, path: Path): Path? {
        super.handlePath(globalVar, path) ?: return null

        if (globalVar == Global.ANDROID_SDK_HOME && !validateAndroidSdkHomeValue(path)) {
            return null
        }

        return if (globalVar.androidLeaf != null) {
            path.resolve(globalVar.androidLeaf)
        } else {
            path
        }
    }

    private fun validateAndroidSdkHomeValue(path: Path): Boolean {
        if (!CancellableFileIo.isDirectory(path)) {
            return false
        }

        if (isSdkRootWithoutDotAndroid(path)) {
            val message =
                    """
                        ANDROID_SDK_HOME is set to the root of your SDK: $path
                        ANDROID_SDK_HOME was meant to be the parent path of the preference folder expected by the Android tools.
                        It is now deprecated.

                        To set a custom preference folder location, use ANDROID_USER_HOME.

                        It should NOT be set to the same directory as the root of your SDK.
                        To set a custom SDK location, use ANDROID_HOME.
                        """.trimIndent()

            if (logger != null) {
                logger.warning(message)
            } else {
                throw AndroidLocationsException(message)
            }
        }

        return true
    }

    private fun isSdkRootWithoutDotAndroid(folder: Path): Boolean {
        return (folder.hasSubFolder("platforms") &&
                folder.hasSubFolder("platform-tools") &&
                !folder.hasSubFolder(FOLDER_DOT_ANDROID))
    }

    private fun Path.hasSubFolder(subFolder: String): Boolean {
        return CancellableFileIo.isDirectory(resolve(subFolder))
    }
}

private fun <T: LocationValue> combineLocationValuesIntoMessage(
    values: List<T>,
    prefix: String = "",
    suffix: String = "",
    modifier: ((T) -> String?)? = null
): String {

    val buffer = StringBuffer(prefix)

    if (values.isNotEmpty()) {
        buffer.append('\n')
    }
    for (value in values.sorted()) {
        // use the path instead of correct since this is what is injected by the user
        val modifierStr = modifier?.let { it(value)?.let{ "(${it})" } } ?: ""
        buffer.append("\n- ${value.propertyName}(${value.queryType}): ${value.value}$modifierStr")
    }

    if (suffix.isNotBlank()) {
        buffer.append("\n\n$suffix")
    }

    return buffer.toString()
}


/**
 * Enum describing which variables to check and whether they should be checked via
 * [EnvironmentProvider.getSystemProperty] or [EnvironmentProvider.getEnvVariable] or both.
 */
private enum class Global(
    val propName: String,
    val isSysProp: Boolean,
    val isEnvVar: Boolean,
    val androidLeaf: String? = FOLDER_DOT_ANDROID,
    val mustExist: Boolean = true
) {
    ANDROID_USER_HOME(
        propName = AbstractAndroidLocations.ANDROID_USER_HOME,
        isSysProp = true,
        isEnvVar = true,
        androidLeaf = null,
        mustExist = false
    ),
    ANDROID_AVD_HOME(
        propName = "ANDROID_AVD_HOME",
        isSysProp = true,
        isEnvVar = true,
        androidLeaf = null
    ),
    ANDROID_SDK_HOME(
        propName = "ANDROID_SDK_HOME",
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
