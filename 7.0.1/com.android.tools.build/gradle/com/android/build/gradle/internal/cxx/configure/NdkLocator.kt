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
import com.android.SdkConstants.NDK_DIR_PROPERTY
import com.android.build.gradle.internal.SdkHandler
import com.android.build.gradle.internal.SdkLocator
import com.android.build.gradle.internal.cxx.caching.cache
import com.android.build.gradle.internal.cxx.configure.SdkSourceProperties.Companion.SdkSourceProperty.SDK_PKG_REVISION
import com.android.build.gradle.internal.cxx.logging.PassThroughDeduplicatingLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.PassThroughPrefixingLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.ThreadLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.builder.errors.IssueReporter
import com.android.repository.Revision
import com.android.utils.FileUtils.join
import com.android.utils.cxx.CxxDiagnosticCode.NDK_CORRUPTED
import com.android.utils.cxx.CxxDiagnosticCode.NDK_DIR_IS_DEPRECATED
import com.android.utils.cxx.CxxDiagnosticCode.NDK_IS_AMBIGUOUS
import com.android.utils.cxx.CxxDiagnosticCode.NDK_IS_INVALID
import com.android.utils.cxx.CxxDiagnosticCode.NDK_VERSION_IS_INVALID
import com.android.utils.cxx.CxxDiagnosticCode.NDK_VERSION_IS_UNMATCHED
import com.android.utils.cxx.CxxDiagnosticCode.NDK_VERSION_UNSUPPORTED
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.InvalidUserDataException
import java.io.File

/**
 * The hard-coded NDK version for this Android Gradle Plugin.
 */
const val ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION = "21.4.7075529"

/**
 * Logic to find the NDK and optionally download it if not found.
 *
 * The high-level behavior of this function is:
 * (1) Use android.ndkPath if possible
 * (2) Use ndk.dir if possible
 * (3) Otherwise, use $SDK/ndk/$ndkVersion if possible
 * (4) Otherwise, use $SDK/ndk-bundle if possible.
 * (5) Otherwise, return null
 *
 * @param userSettings Information that comes from the user in some way. Includes
 *   android.ndkVersion, for example.
 * @param getNdkSourceProperties Given a folder to an NDK, this function returns source.properties
 *   content or null if that file doesn't exist.
 * @param sdkHandler If present, used to download and install NDK with [SdkHandler.installNdk]. If
 *   null, then don't download.
 * @return If the NDK was located, NdkLocatorRecord contains the path to it and the parsed
 *   [Revision] that the path refers to. If the NDK was not located then null is returned.
 */
private fun findNdkPathImpl(
    userSettings: NdkLocatorKey,
    getNdkSourceProperties: (File) -> SdkSourceProperties?,
    sdkHandler: SdkHandler?
): NdkLocatorRecord? {
    with(userSettings) {

        // Record status of user-supplied information
        logUserInputs(userSettings)

        // Function to get the parsed Pkg.Revision, return null if that failed for some reason.
        fun getNdkFolderRevision(ndkDirFolder: File) =
            getNdkFolderParsedRevision(ndkDirFolder, getNdkSourceProperties)

        // Try to get the parsed revision for the requested version. If it's unparseable then
        // emit an error and return.
        val revisionFromNdkVersion =
            parseRevision(getNdkVersionOrDefault(ndkVersionFromDsl)) ?: return null

        // If android.ndkPath value is present then use it.
        if (!ndkPathFromDsl.isNullOrBlank()) {
            val ndkPathFolder = File(ndkPathFromDsl)
            val revisionFromNdkPath = getNdkFolderRevision(ndkPathFolder)
            if (revisionFromNdkPath == null) {
                errorln(
                    NDK_CORRUPTED,
                    "Location specified by android.ndkPath ($ndkPathFromDsl) did not contain " +
                            "a valid NDK and couldn't be used"
                )
                return null
            }
            if (!ndkDirProperty.isNullOrBlank()) {
                errorln(
                    NDK_IS_AMBIGUOUS,
                    "Both android.ndkPath and ndk.dir in local.properties are set"
                )
                return null
            }
            if (!ndkVersionFromDsl.isNullOrBlank()) {
                if (revisionFromNdkVersion != revisionFromNdkPath) {
                    errorln(
                        NDK_IS_AMBIGUOUS,
                        "android.ndkVersion is [$revisionFromNdkVersion] " +
                                "but android.ndkPath $ndkPathFolder refers to a different version " +
                                "[$revisionFromNdkPath]."
                    )
                    return null
                }
            }
            return NdkLocatorRecord(ndkPathFolder, revisionFromNdkPath)
        }

        // If ndk.dir value is present then use it.
        if (!ndkDirProperty.isNullOrBlank()) {
            val ndkDirFolder = File(ndkDirProperty)
            val revision = getNdkFolderRevision(ndkDirFolder)
            if (revision != null) {
                // If the user request a version in android.ndkVersion and it doesn't agree with
                // the version of the NDK supplied by ndk.dir then report an error.
                if (revision != revisionFromNdkVersion && ndkVersionFromDsl != null) {
                    errorln(
                        NDK_VERSION_IS_UNMATCHED,
                        "NDK from ndk.dir at $ndkDirFolder had version [$revision] " +
                                "which disagrees with android.ndkVersion [$revisionFromNdkVersion]"
                    )
                    return null
                }
                val resolutionWithNdkDir = NdkLocatorRecord(ndkDirFolder, revision)

                // Check whether this same NDK folder would be located if ndk.dir was deleted
                // from local.properties and android.ndkVersion was set to this NDK version.
                // If so, we can suggest deleting ndk.dir.
                infoln("Checking whether deleting ndk.dir and setting " +
                        "android.ndkVersion to [$revision] would result in the same NDK")
                val resolutionWithoutNdkDir = PassThroughPrefixingLoggingEnvironment(
                    tag = "ndk.dir delete check",
                    treatAllMessagesAsInfo = true // We don't want hypothetical warnings and errors
                    ).use {
                        val resolutionWithoutNdkDir = findNdkPathImpl(
                            userSettings.copy(
                                ndkVersionFromDsl = revision.toString(),
                                ndkDirProperty = null
                            ),
                            getNdkSourceProperties,
                            sdkHandler = null // Don't want to download hypothetical NDK
                        )
                        if (resolutionWithoutNdkDir == resolutionWithNdkDir) {
                            infoln("Deleting ndk.dir and setting android.ndkVersion to " +
                                    "[$revision] would result in the same NDK.")
                        } else {
                            infoln("Deleting ndk.dir and setting android.ndkVersion to " +
                                    "[$revision] would *not* result in the same NDK.")
                        }
                        resolutionWithoutNdkDir
                    }
                if (resolutionWithNdkDir == resolutionWithoutNdkDir) {
                    // Deleting ndk.dir and setting android.ndkDir to 'revision' would
                    // result in exactly the same NDK being found so the deprecation
                    // warning can indicate it's safe to delete.
                    warnln(
                        NDK_DIR_IS_DEPRECATED,
                        "NDK was located by using ndk.dir property. This method is " +
                                "deprecated and will be removed in a future release. Please " +
                                "delete ndk.dir from local.properties and set android.ndkVersion " +
                                "to [$revision] in all native modules in the project. " +
                                "https://developer.android.com/r/studio-ui/ndk-dir"
                    )
                    return resolutionWithNdkDir
                }

                if (resolutionWithoutNdkDir == null) {
                    // Couldn't resolve any NDK after ndk.dir was removed.
                    warnln(
                        NDK_DIR_IS_DEPRECATED,
                        "NDK was located by using ndk.dir property. This method is " +
                                "deprecated and will be removed in a future release. Please use " +
                                "android.ndkVersion or android.ndkPath in build.gradle to specify " +
                                "the NDK to use. https://developer.android.com/r/studio-ui/ndk-dir"
                    )
                    return resolutionWithNdkDir
                }

                // Resolved an NDK after ndk.dir was removed, but it wasn't the same.
                warnln(
                    NDK_DIR_IS_DEPRECATED,
                    "NDK was located by using ndk.dir property. This method is " +
                            "deprecated and will be removed in a future release. If you delete " +
                            "ndk.dir from local.properties and set android.ndkVersion to " +
                            "[$revision] then NDK at ${resolutionWithoutNdkDir.ndk} will be " +
                            "used. https://developer.android.com/r/studio-ui/ndk-dir"
                )

                return resolutionWithNdkDir
            }
            errorln(
                NDK_IS_INVALID,
                "Location specified by ndk.dir ($ndkDirProperty) did not contain a valid " +
                        "NDK and couldn't be used"
            )
            return null
        }

        // At this point, the only remaining options are found in the SDK folder. So if the SDK
        // folder value is missing then don't search for sub-folders.
        if (sdkFolder != null) {
            // If a folder exists under $SDK/ndk/$ndkVersion then use it.
            val versionedNdkPath = File(File(sdkFolder, FD_NDK_SIDE_BY_SIDE), "$revisionFromNdkVersion")
            val sideBySideRevision = getNdkFolderRevision(versionedNdkPath)
            if (sideBySideRevision != null) {
                return NdkLocatorRecord(versionedNdkPath, sideBySideRevision)
            }

            // If $SDK/ndk-bundle exists and matches the requested version then use it.
            val ndkBundlePath = File(sdkFolder, FD_NDK)
            val bundleRevision = getNdkFolderRevision(ndkBundlePath)
            if (bundleRevision != null && bundleRevision == revisionFromNdkVersion) {
                return NdkLocatorRecord(ndkBundlePath, bundleRevision)
            }
        }

        if (sdkHandler == null) {
            // Caller requested no NDK download by passing sdkHandler == null.
            return null
        }

        infoln("No NDK was found. Trying to download it now.")
        val downloaded = sdkHandler.installNdk(revisionFromNdkVersion)

        if (downloaded != null) {
            infoln("NDK $revisionFromNdkVersion was downloaded to $downloaded. Using that.")
            return NdkLocatorRecord(downloaded, revisionFromNdkVersion)
        }

        // Throw error expected by Android Studio. If the text isn't this, then Android Studio won't
        // recognize the error and provide hyperlink to install NDK.
        // TODO(b/131320700) convert to errorln(..) which requires co-updating Android Studio.
        throw InvalidUserDataException(
            "NDK not configured. Download " +
                    "it with SDK manager. Preferred NDK version is " +
                    "'$ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION'. ")
    }
}

/**
 * Given a candidate NDK folder, get Pkg.Revision as parsed Revision.
 * Return null if:
 * - source.properties file doesn't exist
 * - Pkg.Revision in source.properties doesn't exist
 * - Pkg.Revision can not be parsed as Revision
 */
fun getNdkFolderParsedRevision(
    ndkDirFolder: File,
    getNdkSourceProperties: (File) -> SdkSourceProperties?): Revision? {
    val properties = getNdkSourceProperties(ndkDirFolder)
    if (properties == null) {
        infoln("Folder $ndkDirFolder does not exist. Ignoring.")
        return null
    }
    val packageRevision = properties.getValue(SDK_PKG_REVISION)
    if (packageRevision == null) {
        errorln(
            NDK_CORRUPTED,
            "Folder $ndkDirFolder has no Pkg.Revision in source.properties. Ignoring."
        )
        return null
    }
    return parseRevision(packageRevision)
}

/**
 * Log information about user-defined inputs to locator.
 */
private fun logUserInputs(userSettings : NdkLocatorKey) {
    with(userSettings) {
        infoln("android.ndkVersion from module build.gradle is [${ndkVersionFromDsl ?: "not set"}]")
        infoln("android.ndkPath from module build.gradle is ${ndkPathFromDsl ?: "not set"}")
        infoln("$NDK_DIR_PROPERTY in local.properties is ${ndkDirProperty ?: "not set"}")
        infoln("Not considering ANDROID_NDK_HOME because support was removed after deprecation period.")

        if (sdkFolder != null) {
            infoln("sdkFolder is $sdkFolder")
            val sxsRoot = join(sdkFolder, "ndk")
            if (!sxsRoot.isDirectory) {
                infoln("NDK side-by-side folder from sdkFolder $sxsRoot does not exist")
            }
        } else {
            infoln("sdkFolder is not set")
        }
    }
}

/**
 * If the user specified android.ndkVersion then return it. Otherwise, return the default version
 */
private fun getNdkVersionOrDefault(ndkVersionFromDsl : String?) =
    if (ndkVersionFromDsl.isNullOrBlank()) {
        infoln(
            "Because no explicit NDK was requested, the default version " +
                    "[$ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION] for this Android Gradle " +
                    "Plugin will be used"
        )
        ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION
    } else {
        ndkVersionFromDsl
    }

/**
 * Parse the given version and ensure that it has exactly precision of 3 or higher
 */
private fun parseRevision(version : String) : Revision? {
    try {
        val revision =
            stripPreviewFromRevision(Revision.parseRevision(version))
        if (revision.toIntArray(true).size < 3) {
            errorln(
                NDK_IS_AMBIGUOUS,
                "Specified NDK version [$version] does not have " +
                        "enough precision. Use major.minor.micro in version."
            )
            return null
        }
        return revision
    } catch (e: NumberFormatException) {
        errorln(NDK_VERSION_IS_INVALID, "Requested NDK version '$version' could not be parsed")
        return null
    }
}

/**
 * Given a path to NDK, return the content of source.properties.
 * Returns null if the file isn't found.
 */
@VisibleForTesting
fun getNdkVersionInfo(ndkRoot: File): SdkSourceProperties? {
    if (!ndkRoot.exists()) {
        return null
    }
    val sourceProperties = File(ndkRoot, "source.properties")
    if (!sourceProperties.exists()) {
        val releaseTxt = ndkRoot.resolve("RELEASE.TXT")
        if (releaseTxt.exists()) {
            errorln(NDK_VERSION_UNSUPPORTED, "NDK at $ndkRoot is not supported (pre-r11)")
            return null
        }
        errorln(NDK_CORRUPTED, "NDK at $ndkRoot did not have a source.properties file")
        return null
    }
    return SdkSourceProperties.fromInstallFolder(ndkRoot)
}

/**
 * Given a path to an NDK root folder, return a list of the NDKs installed there.
 */
@VisibleForTesting
fun getNdkVersionedFolders(ndkVersionRoot: File): List<String> {
    if (!ndkVersionRoot.isDirectory) {
        return listOf()
    }
    return ndkVersionRoot.list()!!.filter { File(ndkVersionRoot, it).isDirectory }
}

/**
 * If the revision contains a preview element (like rc2) then strip it.
 */
private fun stripPreviewFromRevision(revision : Revision) : Revision {
    val parts = revision.toIntArray(false)
    return when(parts.size) {
        1 -> Revision(parts[0])
        2 -> Revision(parts[0], parts[1])
        else -> Revision(parts[0], parts[1], parts[2])
    }
}

/**
 * Key used for caching the results of NDK resolution.
 */
data class NdkLocatorKey(
    val ndkVersionFromDsl: String?,
    val ndkPathFromDsl: String?,
    val ndkDirProperty: String?,
    val sdkFolder: File?,
    val sideBySideNdkFolderNames : List<String>
)

/**
 * The result of NDK resolution saved into cache.
 */
data class NdkLocatorRecord(
    val ndk: File,
    val revision: Revision
)

/**
 * Wraps findNdkPathImpl with caching.
 *
 * The function getNdkSourceProperties returns content of source.properties in a specific NDK
 * or null if it doesn't exist or there's is an incompatibility problem.
 */
@VisibleForTesting
fun findNdkPathImpl(
    ndkVersionFromDsl: String?,
    ndkPathFromDsl: String?,
    ndkDirProperty: String?,
    sdkFolder: File?,
    ndkVersionedFolderNames: List<String>,
    getNdkSourceProperties: (File) -> SdkSourceProperties?,
    sdkHandler: SdkHandler?
): NdkLocatorRecord? {
    val key = NdkLocatorKey(
        ndkVersionFromDsl,
        ndkPathFromDsl,
        ndkDirProperty,
        sdkFolder,
        ndkVersionedFolderNames)
    // Result of NDK location could be cached at machine level.
    // Here, it's cached at module level instead because uncleanable caches can lead to difficult bugs.
    return cache(key, {
        PassThroughDeduplicatingLoggingEnvironment().use {
            findNdkPathImpl(
                key,
                getNdkSourceProperties,
                sdkHandler
            )
        }
    })
}
data class NdkLocator(
    private val issueReporter: IssueReporter,
    private val ndkVersionFromDsl: String?,
    private val ndkPathFromDsl: String?,
    private val projectDir: File,
    private val sdkHandler: SdkHandler) {
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
     *  (2) Don't specify a folder which implies an NDK from the SDK folder should be used
     *
     * If the user specifies android.ndkVersion in build.gradle then that version must be available
     * or it is an error. If no such version is specified then the default version is used.
     */
    fun findNdkPath(downloadOkay: Boolean): NdkLocatorRecord? {
        val properties = gradleLocalProperties(projectDir)
        val sdkPath = SdkLocator.getSdkDirectory(projectDir, issueReporter)
        return findNdkPathImpl(
            ndkVersionFromDsl,
            ndkPathFromDsl,
            properties.getProperty(NDK_DIR_PROPERTY),
            sdkPath,
            getNdkVersionedFolders(File(sdkPath, FD_NDK_SIDE_BY_SIDE)),
            ::getNdkVersionInfo,
            if (downloadOkay) sdkHandler else null
        )
    }
}
