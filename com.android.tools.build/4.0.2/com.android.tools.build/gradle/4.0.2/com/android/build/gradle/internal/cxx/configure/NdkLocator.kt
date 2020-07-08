/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.SdkConstants.FD_NDK
import com.android.SdkConstants.FD_NDK_SIDE_BY_SIDE
import com.android.SdkConstants.FN_SOURCE_PROP
import com.android.SdkConstants.NDK_DIR_PROPERTY
import com.android.build.gradle.internal.SdkLocator
import com.android.build.gradle.internal.cxx.caching.cache
import com.android.build.gradle.internal.cxx.configure.LocationType.ANDROID_NDK_HOME_LOCATION
import com.android.build.gradle.internal.cxx.configure.LocationType.NDK_BUNDLE_FOLDER_LOCATION
import com.android.build.gradle.internal.cxx.configure.LocationType.NDK_DIR_LOCATION
import com.android.build.gradle.internal.cxx.configure.LocationType.NDK_VERSIONED_FOLDER_LOCATION
import com.android.build.gradle.internal.cxx.configure.SdkSourceProperties.Companion.SdkSourceProperty.SDK_PKG_REVISION
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.PassThroughDeduplicatingLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.builder.errors.IssueReporter
import com.android.repository.Revision
import com.android.utils.FileUtils.join
import com.google.common.annotations.VisibleForTesting
import java.io.File
import java.io.FileNotFoundException

/**
 * The hard-coded NDK version for this Android Gradle Plugin.
 */
const val ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION = "21.0.6113669"

private enum class LocationType(val tag: String) {
    // These are in order of preferred in the case when versions are identical.
    NDK_VERSIONED_FOLDER_LOCATION("in SDK ndk folder"),
    NDK_BUNDLE_FOLDER_LOCATION("in SDK ndk-bundle folder"),
    NDK_DIR_LOCATION("by $NDK_DIR_PROPERTY"),
    ANDROID_NDK_HOME_LOCATION("by ANDROID_NDK_HOME");
}

private data class Location(val type: LocationType, val ndkRoot: File)

/**
 * Logic to find the NDK.
 *
 * ndkVersionFromDsl - the literal version string from build.gradle. null if there was nothing
 * ndkDirProperty - the string ndk.dir from local.settings
 * androidNdkHomeEnvironmentVariable - the value of ANDROID_NDK_HOME from the environment
 * sdkFolder - the folder to the SDK if it exists
 * getNdkVersionedFolderNames - function that returns the NDK folders under $SDK/ndk
 * getNdkSourceProperties - given a folder to an NDK, this function returns the version of that NDK.
 */
private fun findNdkPathImpl(
    ndkDirProperty: String?,
    androidNdkHomeEnvironmentVariable: String?,
    sdkFolder: File?,
    ndkVersionFromDsl: String?,
    sideBySideNdkFolderNames: List<String>,
    getNdkSourceProperties: (File) -> SdkSourceProperties?
): File? {

    // Record status of user-supplied information
    infoln("android.ndkVersion from module build.gradle is ${ndkVersionFromDsl ?: "not set"}")
    infoln("$NDK_DIR_PROPERTY in local.properties is ${ndkDirProperty ?: "not set"}")
    infoln(
        "ANDROID_NDK_HOME environment variable is " +
                (androidNdkHomeEnvironmentVariable ?: "not set")
    )
    if (sdkFolder != null) {
        infoln("sdkFolder is $sdkFolder")
        val sxsRoot = join(sdkFolder, "ndk")
        if (!sxsRoot.isDirectory) {
            infoln("NDK side-by-side folder from sdkFolder $sxsRoot does not exist")
        }
    } else {
        infoln("sdkFolder is not set")
    }

    /**
     * If NDK version is not specified in DSL, ndk.dir, or ANDROID_NDK_HOME then use the current
     * gradle default version of NDK.
     */
    val ndkVersionOrDefault = if (ndkVersionFromDsl.isNullOrBlank() &&
        ndkDirProperty.isNullOrBlank() &&
        androidNdkHomeEnvironmentVariable.isNullOrBlank()
    ) {
        infoln(
            "Because no explicit NDK was requested, the default version " +
                    "'$ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION' for this Android Gradle " +
                    "Plugin will be used"
        )
        ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION
    } else {
        ndkVersionFromDsl
    }

    // ANDROID_NDK_HOME is deprecated
    if (androidNdkHomeEnvironmentVariable != null) {
        warnln("Support for ANDROID_NDK_HOME is deprecated and will be removed in the future. Use android.ndkVersion in build.gradle instead.")
    }

    // Record that a location was considered and rejected and for what reason
    fun considerAndReject(location: Location, reason: String) {
        infoln("Rejected ${location.ndkRoot} ${location.type.tag} because $reason")
    }

    val foundLocations = mutableListOf<Location>()
    if (ndkDirProperty != null) {
        foundLocations += Location(NDK_DIR_LOCATION, File(ndkDirProperty))
    }
    if (androidNdkHomeEnvironmentVariable != null) {
        foundLocations += Location(
            ANDROID_NDK_HOME_LOCATION,
            File(androidNdkHomeEnvironmentVariable)
        )
    }
    if (sdkFolder != null) {
        foundLocations += Location(NDK_BUNDLE_FOLDER_LOCATION, File(sdkFolder, FD_NDK))
    }

    // Parse the user-supplied version and give an error if it can't be parsed.
    var ndkVersionFromDslRevision: Revision? = null
    if (ndkVersionOrDefault != null) {
        try {
            ndkVersionFromDslRevision =
                stripPreviewFromRevision(Revision.parseRevision(ndkVersionOrDefault))
            if (ndkVersionFromDslRevision.toIntArray(true).size < 3) {
                errorln(
                    "Specified android.ndkVersion '$ndkVersionOrDefault' does not have " +
                            "enough precision. Use major.minor.micro in version."
                )
                return null
            }
        } catch (e: NumberFormatException) {
            errorln("Requested NDK version '$ndkVersionFromDsl' could not be parsed")
            return null
        }
    }

    if (sdkFolder != null) {
        val versionRoot = File(sdkFolder, FD_NDK_SIDE_BY_SIDE)
        foundLocations += sideBySideNdkFolderNames
            .map { version ->
                Location(
                    NDK_VERSIONED_FOLDER_LOCATION,
                    File(versionRoot, version)
                )
            }
    }

    // Log all found locations
    foundLocations.forEach { location ->
        infoln("Considering ${location.ndkRoot} ${location.type.tag}")
    }

    // Eliminate those that don't look like NDK folders
    val versionedLocations = foundLocations
        .mapNotNull { location ->
            val versionInfo = getNdkSourceProperties(location.ndkRoot)
            when {
                versionInfo == null -> {
                    if (location.ndkRoot.resolve("RELEASE.TXT").exists()) {
                        considerAndReject(location, "it contains an unsupported (pre-r11) NDK")
                    } else {
                        considerAndReject(location, "that location has no $FN_SOURCE_PROP")
                    }
                    null
                }
                versionInfo.getValue(SDK_PKG_REVISION) == null -> {
                    considerAndReject(
                        location, "that location had $FN_SOURCE_PROP " +
                                "with no ${SDK_PKG_REVISION.key}"
                    )
                    null
                }
                else -> {
                    val revision = versionInfo.getValue(SDK_PKG_REVISION)!!
                    try {
                        val revision = Revision.parseRevision(revision)
                        // Trim preview information since we expect to match major.minor.micro only
                        Pair(location, stripPreviewFromRevision(revision))
                    } catch (e: NumberFormatException) {
                        considerAndReject(
                            location, "that location had " +
                                    "source.properties with invalid ${SDK_PKG_REVISION.key}=$revision"
                        )
                        null
                    }
                }
            }
        }
        .sortedWith(compareBy({ -it.first.type.ordinal }, { it.second }))
        .asReversed()

    // From the existing NDKs find the highest. We'll use this as a fall-back in case there's an
    // error. We still want to succeed the sync and recover as best we can.
    val highest = versionedLocations.firstOrNull()

    if (highest == null) {
        // The text of this message shouldn't change without also changing the corresponding
        // hotfix in Android Studio that recognizes this text
        if (ndkVersionFromDsl == null) {
            warnln(
                "Compatible side by side NDK version was not found. " +
                        "Default is $ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION."
            )
        } else {
            warnln(
                "Compatible side by side NDK version was not found for android.ndkVersion " +
                        "'$ndkVersionFromDslRevision'"
            )
        }
        return null
    }

    // If the user requested a specific version then honor it now
    if (ndkVersionFromDslRevision != null) {
        // If the user specified ndk.dir then it must be used. It must also match the version
        // supplied in build.gradle.
        if (ndkDirProperty != null) {
            val ndkDirLocation = versionedLocations.find { (location, _) ->
                location.type == NDK_DIR_LOCATION
            }
            if (ndkDirLocation == null) {
                errorln(
                    "Location specified by ndk.dir ($ndkDirProperty) did not contain a " +
                            "valid NDK and so couldn't satisfy the required NDK version " +
                            ndkVersionOrDefault
                )
                return null
            } else {
                val (location, version) = ndkDirLocation
                if (isAcceptableNdkVersion(version, ndkVersionFromDslRevision)) {
                    infoln(
                        "Choosing ${location.ndkRoot} from $NDK_DIR_PROPERTY which had the requested " +
                                "version $ndkVersionOrDefault"
                    )
                } else {
                    errorln(
                        "Requested NDK version $ndkVersionOrDefault did not match the version " +
                                "$version requested by $NDK_DIR_PROPERTY at ${location.ndkRoot}"
                    )
                    return null
                }
                return location.ndkRoot
            }
        }

        // If not ndk.dir then take the version that matches the requested NDK version
        val matchingLocations = versionedLocations
            .filter { (_, sourceProperties) ->
                isAcceptableNdkVersion(sourceProperties, ndkVersionFromDslRevision)
            }
            .toList()

        if (matchingLocations.isEmpty()) {
            // No versions matched the requested revision
            versionedLocations.onEach { (location, version) ->
                considerAndReject(
                    location,
                    "that NDK had version $version which didn't " +
                            "match the requested version $ndkVersionOrDefault"
                )
            }
            if (versionedLocations.isNotEmpty()) {
                val available =
                    versionedLocations
                        .sortedBy { (_, version) -> version }
                        .joinToString(", ") { (_, version) -> version.toString() }
                errorln("No version of NDK matched the requested version $ndkVersionOrDefault. Versions available locally: $available")
            } else {
                errorln("No version of NDK matched the requested version $ndkVersionOrDefault")
            }
            return null
        }

        // There could be multiple. Choose the preferred location and if there are multiple in that
        // location then choose the highest version there.
        val foundNdkRoot = matchingLocations.first().first.ndkRoot

        if (matchingLocations.size > 1) {
            infoln(
                "Found ${matchingLocations.size} NDK folders that matched requested " +
                        "version $ndkVersionFromDslRevision:"
            )
            matchingLocations.forEachIndexed { index, (location, _) ->
                infoln(" (${index + 1}) ${location.ndkRoot} ${location.type.tag}")
            }
            infoln("  choosing $foundNdkRoot")
        } else {
            infoln("Found requested NDK version $ndkVersionFromDslRevision at $foundNdkRoot")
        }
        return foundNdkRoot

    } else {
        // If the user specified ndk.dir then it must be used.
        if (ndkDirProperty != null) {
            val ndkDirLocation =
                versionedLocations.find { (location, _) ->
                    location.type == NDK_DIR_LOCATION
                }
            if (ndkDirLocation == null) {
                errorln(
                    "Location specified by ndk.dir ($ndkDirProperty) did not contain a " +
                            "valid NDK and and couldn't be used"
                )

                infoln(
                    "Using ${highest.first.ndkRoot} which is " +
                            "version ${highest.second} as fallback but build will fail"
                )
                return null
            }
            val (location, version) = ndkDirLocation
            infoln("Found requested ndk.dir (${location.ndkRoot}) which has version ${version}")
            return location.ndkRoot
        }

        // No NDK version was requested.
        infoln(
            "No user requested version, choosing ${highest.first.ndkRoot} which is " +
                    "version ${highest.second}"
        )
        return highest.first.ndkRoot
    }
}

data class NdkLocatorKey(
    val ndkVersionFromDsl: String?,
    val ndkDirProperty: String?,
    val androidNdkHomeEnvironmentVariable: String?,
    val sdkFolder: File?,
    val sideBySideNdkFolderNames : List<String>
)

@VisibleForTesting
fun findNdkPathImpl(
    ndkVersionFromDsl: String?,
    ndkDirProperty: String?,
    androidNdkHomeEnvironmentVariable: String?,
    sdkFolder: File?,
    getNdkVersionedFolderNames: (File) -> List<String>,
    getNdkSourceProperties: (File) -> SdkSourceProperties?
): File? {
    val sideBySideNdkFolderNames = if(sdkFolder != null) getNdkVersionedFolderNames(join(sdkFolder, FD_NDK_SIDE_BY_SIDE)) else listOf()
    val key = NdkLocatorKey(
        ndkVersionFromDsl,
        ndkDirProperty,
        androidNdkHomeEnvironmentVariable,
        sdkFolder,
        if(sdkFolder != null) getNdkVersionedFolderNames(join(sdkFolder, FD_NDK_SIDE_BY_SIDE)) else listOf())
    // Result of NDK location could be cached at machine level.
    // Here, it's cached at module level instead because uncleanable caches can lead to difficult bugs.
    return cache(key, {
        with(key) {
            PassThroughDeduplicatingLoggingEnvironment().use { loggingEnvironment ->
                val ndkFolder = findNdkPathImpl(
                    this.ndkDirProperty,
                    this.androidNdkHomeEnvironmentVariable,
                    this.sdkFolder,
                    this.ndkVersionFromDsl,
                    sideBySideNdkFolderNames,
                    getNdkSourceProperties
                )
                NdkLocatorRecord(
                    ndkFolder = ndkFolder
                )
            }
        }
    }).ndkFolder
}

/**
 * Returns true if the found revision sourcePropertiesRevision can satisfy the user's requested
 * revision from build.gradle.
 */
private fun isAcceptableNdkVersion(
    sourcePropertiesRevision: Revision, revisionFromDsl: Revision
): Boolean {
    val parts = revisionFromDsl.toIntArray(true)
    return when (parts.size) {
        3, 4 -> sourcePropertiesRevision == revisionFromDsl
        2 -> revisionFromDsl.major == sourcePropertiesRevision.major &&
                revisionFromDsl.minor == sourcePropertiesRevision.minor
        1 -> revisionFromDsl.major == sourcePropertiesRevision.major
        else -> throw RuntimeException("Unexpected")
    }
}

@VisibleForTesting
fun getNdkVersionInfo(ndkRoot: File): SdkSourceProperties? {
    return try {
        SdkSourceProperties.fromInstallFolder(ndkRoot)
    } catch (e: FileNotFoundException) {
        null
    }
}

@VisibleForTesting
fun getNdkVersionedFolders(ndkVersionRoot: File): List<String> {
    if (!ndkVersionRoot.isDirectory) {
        return listOf()
    }
    return ndkVersionRoot.list()!!.filter { File(ndkVersionRoot, it).isDirectory }
}

fun stripPreviewFromRevision(revision : Revision) : Revision
{
    var parts = revision.toIntArray(false)
    return when(parts.size) {
        1 -> Revision(parts[0])
        2 -> Revision(parts[0], parts[1])
        else -> Revision(parts[0], parts[1], parts[2])
    }
}

data class NdkLocatorRecord(
    val ndkFolder: File?
)

/**
 * There are three possible physical locations for NDK:
 *
 *  (1) SDK unversioned: $(SDK)/ndk-bundle
 *  (2) SDK versioned: $(SDK)/ndk/18.1.2 (where 18.1.2 is an example)
 *  (3) Custom: Any location on disk
 *
 * There are several ways the user can tell Android Gradle Plugin where to find the NDK
 *
 *  (1) Set an explicit folder in local.settings for ndk.dir
 *  (2) Specify an explicit folder with environment variable ANDROID_NDK_HOME
 *  (3) Don't specify a folder which implies an NDK from the SDK folder should be used
 *
 * If the user specifies android.ndkVersion in build.gradle then that version must be available
 * or it is an error. If no such version is specifies then the highest version available is
 * used.
 *
 * Failure behaviour -- even if there is a failure, this function tries to at least return *some*
 * NDK so that the gradle Sync can continue.
 */
fun findNdkPath(
    issueReporter: IssueReporter,
    ndkVersionFromDsl: String?,
    projectDir: File
): File? {
    IssueReporterLoggingEnvironment(issueReporter).use {
        val properties = gradleLocalProperties(projectDir)
        val sdkPath = SdkLocator.getSdkDirectory(projectDir, issueReporter)
        return findNdkPathImpl(
            ndkVersionFromDsl,
            properties.getProperty(NDK_DIR_PROPERTY),
            System.getenv("ANDROID_NDK_HOME"),
            sdkPath,
            ::getNdkVersionedFolders,
            ::getNdkVersionInfo
        )
    }
}
