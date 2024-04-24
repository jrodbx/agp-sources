/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.builder.errors.IssueReporter
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.io.File
import java.io.StringReader
import java.util.Properties

/**
 * SdkType represents which kind is the SDK located (or if it's missing).
 */
enum class SdkType {
    REGULAR, PLATFORM, TEST, MISSING
}

/**
 * SdkLocation is the result of calling {@code SdkLocator.getSdkLocation(sourceSet) and contains
 * the located SDK directory and its type.
 */
data class SdkLocation(val directory: File?, val type: SdkType)

/**
 * A SdkLocationSourceSet represents all the variables and information necessary for locating the
 * SDK to be used based on all the possible ways that the user can set their preferences.
 *
 * <p>Create a new instance using {@code SdkLocationSourceSet(gradleProject.rootDir)} and the
 * constructor will collect the proper variables from the local.properties file, the environment
 * variables and system properties.
 *
 * <p>For testing reasons you can override any of the components.
 */
data class SdkLocationSourceSet(
    val projectRoot: File,
    private val providers: ProviderFactory,
    internal val localProperties: Properties = GradleLocalPropertiesFactory.get(projectRoot, providers),
    internal val environmentProperties: Properties = EnvironmentVariablesPropertiesFactory.get(),
    internal val systemProperties: Properties = SystemPropertiesFactory.get())

/**
 * SdkLocator contains the logic to select the SDK based on the variables set by the user.
 *
 * <p>Use {@link SdkLocator.getSdkLocation(sourceSet)} to fetch a {@code SdkLocation} object based
 * on the user preferences represented by the passed {@code sourceSet}. If this sourceSet was used
 * before than the result will be cached.
 *
 * <p>To clear the cache, at the end of the build for example, use {@link SdkLocator.resetCache()}.
 */
object SdkLocator {

    @JvmStatic
    @VisibleForTesting
    var sdkTestDirectory: File? = null

    @VisibleForTesting
    const val ANDROID_DIR_PROPERTY = "android.dir"
    @VisibleForTesting
    const val ANDROID_HOME_SYSTEM_PROPERTY = "android.home"

    // Order defines the preference for matching an SDK directory.
    internal enum class SdkLocationSource(val sdkType: SdkType) {
        TEST_SDK_DIRECTORY(SdkType.TEST) {
            override fun getSdkPathProperty(sourceSet: SdkLocationSourceSet): File? {
                return sdkTestDirectory?.absolutePath?.let {
                    validateSdkPath(it, sourceSet.projectRoot)
                }
            }
        },
        LOCAL_SDK_DIR(SdkType.REGULAR) {
            override fun getSdkPathProperty(sourceSet: SdkLocationSourceSet): File? {
                return sourceSet.localProperties.getProperty(SdkConstants.SDK_DIR_PROPERTY)?.let {
                    validateSdkPath(it, sourceSet.projectRoot)
                }
            }

        }, LOCAL_ANDROID_DIR(SdkType.PLATFORM) { // TODO: Check if this is still used.
            override fun getSdkPathProperty(sourceSet: SdkLocationSourceSet): File? {
                return sourceSet.localProperties.getProperty(ANDROID_DIR_PROPERTY)?.let {
                    validateSdkPath(it, sourceSet.projectRoot)
                }
            }

        }, INJECTED_SDK_HOME(SdkType.REGULAR) {

            override fun getSdkPathProperty(sourceSet: SdkLocationSourceSet): File? {
                // Special Handling:
                // If the query is ANDROID_SDK_ROOT, then also query ANDROID_HOME and compare
                // the values. If both values are set, they must match
                val map = mutableMapOf<String, File>()

                sourceSet.environmentProperties
                    .getProperty(SdkConstants.ANDROID_SDK_ROOT_ENV)
                    ?.let { path ->
                        validateSdkPath(path, sourceSet.projectRoot)?.let {
                            map[SdkConstants.ANDROID_SDK_ROOT_ENV] = it
                        }
                    }

                sourceSet.environmentProperties
                    .getProperty(SdkConstants.ANDROID_HOME_ENV)
                    ?.let { path ->
                        validateSdkPath(path, sourceSet.projectRoot)?.let {
                            map[SdkConstants.ANDROID_HOME_ENV] = it
                        }
                    }

                sourceSet.systemProperties.getProperty(ANDROID_HOME_SYSTEM_PROPERTY)?.let { path ->
                    validateSdkPath(path, sourceSet.projectRoot)?.let {
                        map[ANDROID_HOME_SYSTEM_PROPERTY] = it
                    }
                }

                if (map.isEmpty()) {
                    return null
                }

                if (map.size == 1) {
                    return map.values.single()
                }

                // check if the different entries have different values.
                val reverseMap = map.entries.groupBy { it.value }

                if (reverseMap.size == 1) {
                    // then all points to a single location
                    return reverseMap.keys.single()
                }

                // if not, fail
                var message =
                        """
Several environment variables and/or system properties contain different paths to the SDK.
Please correct and use only one way to inject the SDK location.
""".trimStart()

                for (entry in map.entries.sortedBy { it.key }) {
                    message = "$message\n${entry.key}: ${entry.value}"

                }

                message =
                        """$message

It is recommended to use ANDROID_HOME as other methods are deprecated
""".trimIndent()

                throw RuntimeException(message)
            }
        };

        fun getSdkLocation(sourceSet: SdkLocationSourceSet): SdkLocation? {
            return getSdkPathProperty(sourceSet)?.let { SdkLocation(it, sdkType) }
        }

        abstract fun getSdkPathProperty(sourceSet: SdkLocationSourceSet): File?

        // Basic SDK path validation:
        // * If it's not an absolute path, uses the project rootDir as base.
        // * Check if the path points to a directory in the disk.
        protected fun validateSdkPath(path: String, rootDir: File): File? {
            var sdk = File(path)
            if (!sdk.isAbsolute) {
                // Canonical file transforms paths like: .../userHome/projectRoot/../AndroidSDK
                // Into: .../userHome/AndroidSDK
                // This makes no difference on the behavior of the code, but for error messages is much
                // more clear to present the canonical path to the user.
                sdk = File(rootDir, path).canonicalFile
            }
            return if (sdk.isDirectory) sdk else null
        }
    }

    // We cache by the source set used, so if any property changes we don't use the cache and locate
    // the SDK directory again.
    private var cachedSdkLocationKey: SdkLocationSourceSet? = null
    private var cachedSdkLocation: SdkLocation? = null

    @JvmStatic
    fun getSdkDirectory(
        projectRootDir: File,
        issueReporter: IssueReporter,
        sdkLocationSourceSet: SdkLocationSourceSet
    ): File {
        val sdkLocation =
            getSdkLocation(sdkLocationSourceSet, issueReporter)
        return if (sdkLocation.type == SdkType.MISSING) {
            // This error should have been reported earlier when SdkLocation was created, so we can
            // just return a dummy file here as it won't be used anyway.
            File(projectRootDir, "missingSdkDirectory")
        } else {
            sdkLocation.directory
                ?: error("Directory must not be null when type = ${sdkLocation.type}")
        }
    }

    @JvmStatic
    @Synchronized
    fun getSdkLocation(
        sourceSet: SdkLocationSourceSet,
        issueReporter: IssueReporter
    ): SdkLocation {
        cachedSdkLocationKey?.let {
            if (it == sourceSet) {
                return cachedSdkLocation!!
            }
        }

        for (source in SdkLocationSource.values()) {
            source.getSdkLocation(sourceSet)?.let {
                updateCache(it, sourceSet)
                return it
            }
        }

        SdkLocation(null, SdkType.MISSING).let {
            // Update the cache first, so that even if we fail below with a runtime error, the cache
            // is still updated and the error message will be displayed only once (even in parallel
            // builds).
            updateCache(it, sourceSet)

            val filePath =
                File(sourceSet.projectRoot, SdkConstants.FN_LOCAL_PROPERTIES).absolutePath
            val message =
                "SDK location not found. Define a valid SDK location with an ANDROID_HOME" +
                        " environment variable or by setting the sdk.dir path in your project's" +
                        " local properties file at '$filePath'."
            issueReporter.reportError(IssueReporter.Type.SDK_NOT_SET, message, filePath)

            return it
        }
    }

    @Synchronized
    private fun updateCache(sdkLocation: SdkLocation, sourceSet: SdkLocationSourceSet) {
        cachedSdkLocationKey = sourceSet
        cachedSdkLocation = sdkLocation

    }

    @JvmStatic
    @Synchronized
    fun resetCache() {
        GradleLocalPropertiesFactory.resetCache()
        cachedSdkLocationKey = null
        cachedSdkLocation = null
    }
}

private object GradleLocalPropertiesFactory {

    val cache = mutableMapOf<File, Properties>()

    @Synchronized
    internal fun get(projectRoot: File, providers: ProviderFactory): Properties {
        val properties = Properties()

        val propertiesContent =
            providers.of(PropertiesValueSource::class.java) {
                it.parameters.projectRoot.set(projectRoot)
            }.get()
        StringReader(propertiesContent).use { reader ->
            properties.load(reader)
        }

        cache[projectRoot] = properties
        return properties
    }

    @Synchronized
    internal fun resetCache() {
        cache.clear()
    }

}

abstract class PropertiesValueSource : ValueSource<String, PropertiesValueSource.Params> {
    interface Params: ValueSourceParameters {
        val projectRoot: RegularFileProperty
    }

    override fun obtain(): String {
        val localPropertiesFile =
            File(parameters.projectRoot.get().asFile, SdkConstants.FN_LOCAL_PROPERTIES)
        if (localPropertiesFile.isFile) {
            return localPropertiesFile.readText()
        }
        return ""
    }
}

private object EnvironmentVariablesPropertiesFactory {
    internal fun get(): Properties {
        val properties = Properties()
        System.getenv(SdkConstants.ANDROID_HOME_ENV)?.let {
            properties.setProperty(SdkConstants.ANDROID_HOME_ENV, it) }
        System.getenv(SdkConstants.ANDROID_SDK_ROOT_ENV)?.let {
            properties.setProperty(SdkConstants.ANDROID_SDK_ROOT_ENV, it) }
        return properties
    }
}

private object SystemPropertiesFactory {
    internal fun get(): Properties {
        val properties = Properties()
        System.getProperty(SdkLocator.ANDROID_HOME_SYSTEM_PROPERTY)?.let {
            properties.setProperty(SdkLocator.ANDROID_HOME_SYSTEM_PROPERTY, it) }
        return properties
    }
}
