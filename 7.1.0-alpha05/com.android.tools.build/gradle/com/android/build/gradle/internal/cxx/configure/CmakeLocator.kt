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

import com.android.SdkConstants
import com.android.SdkConstants.FD_CMAKE
import com.android.build.gradle.external.cmake.CmakeUtils
import com.android.build.gradle.external.cmake.CmakeUtils.keepWhileNumbersAndDots
import com.android.build.gradle.internal.cxx.logging.PassThroughDeduplicatingLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.ThreadLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.prefs.AndroidLocationsProvider
import com.android.repository.Revision
import com.android.repository.api.LocalPackage
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.LoggerProgressIndicatorWrapper
import com.android.utils.cxx.CxxDiagnosticCode.CMAKE_IS_MISSING
import com.android.utils.cxx.CxxDiagnosticCode.CMAKE_PACKAGES_SDK
import com.android.utils.cxx.CxxDiagnosticCode.CMAKE_VERSION_IS_INVALID
import com.android.utils.cxx.CxxDiagnosticCode.CMAKE_VERSION_IS_UNSUPPORTED
import java.io.File
import java.io.IOException
import java.util.function.Consumer

/**
 * This is the logic for locating CMake needed for gradle build. This logic searches for CMake in
 * a prescribed order and provides diagnostic information, warnings, and errors useful to the user
 * in understanding how CMake was found or why it wasn't found.
 *
 * There are several pieces of information at play here:
 * (1) The optional CMake version specified in build.gradle under externalNativeBuild.cmake.version.
 * (2) The optional path to CMake folder specified in local.properties file. This file is not meant
 *     to be checked in to version control.
 * (3) The SDK versions that are available in the SDK. Some of these may have been downloaded
 *     already.
 * (4) The system $PATH variable.
 * (5) The version numbers of "fork CMake" which is an older forked version of CMake that has
 *     additional functionality to help with emitting metadata.
 *
 * The version numbers of SDK CMakes are special in that there are two distinct versions for each
 * cmake.exe:
 * (1) A version like "3.6.0-rc2" which is the compiled-in version that is emitted by a call to
 *     cmake --version.
 * (2) A version like "3.6.4111459" which is the SDK's version for the CMake package. In this case,
 *     4111459 is the ADRT "bid" number used for deployment within SDK.
 *
 * The searching algorithm needs to take into account that new SDK versions of CMake will be added
 * in the future. We can't assume we know versions a priori.
 *
 * Only the following CMake versions are supported:
 * (1) A known "fork CMake" from the SDK
 * (2) A version of CMake that supports CMake "server" mode. This mode is supported by CMake 3.7.0
 *     and higher.
 *
 * The search algorithm tries to enforce several invariants:
 * (1) If a cmake is specified in local.properties then that CMake must be used or it's an error.
 * (2) If a CMake version *is* specified in build.gradle externalNativeBuild.cmake.version then
 *     that version must be matched or it's an error.
 * (3) If a CMake version *is not* specified in build.gradle externalNativeBuild.cmake.version then
 *     fork CMake must be used. We won't use a random CMake found on the path.
 * (4) Combining (2) and (3) creates the additional invariant that there is always a specific CMake
 *     version prescribed by a build.gradle. In the case there is no concrete version in
 *     build.gradle there is still a concreted Android Gradle Plugin version which, in turn,
 *     prescribes an exact CMake version.
 *
 * Given these invariants, the algorithm looks in the following locations:
 * (1) SDK
 * (2) $PATH
 * (3) cmake.dir from local.properties
 *
 * Version matching:
 * An incomplete version like "3.12" is allowed in build.gradle. In this case, the version of the
 * found CMake must exactly match what is specified in build.gradle. This means, for example,
 * if build.gradle specifies 3.12 and only version 3.13 is found then 3.13 WON'T be used. User may
 * specify "3" if they want to match the highest version among 3.*.
 *
 * User may also append a "+" to the version. In this case, the highest version is accepted.
 *
 * If multiple are found in PATH then only the first CMake is considered for use.
 *
 * Error Handling:
 * A lot of the logic in this algorithm is dedicated toward giving good error messages in the case
 * that CMake can't be found.
 *
 * - If some CMake versions are found but they don't match the required version then the user is
 *   told what the versions of those CMakes are and where they were found.
 *
 * - If a requested CMake version looks like an SDK version (because it has a bid number in it) then
 *   a specifically constructed error message is emitted that Android Studio can use to
 *   automatically download that version.
 *
 * The various expected error messages are heavily tested in CmakeLocatorTests.kt.
 *
 * Warning:
 * Right now there is only one warning that may be emitted by this algorithm. This is in the case
 * that a CMake.exe is found but it cannot be executed to get its version number. The reason
 * it's not an error is that a matching CMake version may be found in another location. The reason
 * it's not a diagnostic info is that it's an unusual circumstance and the user may end up
 * confused when their expected CMake isn't found.
 *
 */

/**
 * This is the version of fork CMake.
 */
internal const val FORK_CMAKE_SDK_VERSION = "3.6.4111459"
internal val forkCmakeSdkVersionRevision = Revision.parseRevision(FORK_CMAKE_SDK_VERSION)

/**
 *  This is the base version that forked CMake (which has SDK version 3.6.4111459) reports
 *  when cmake --version is called. For backward compatibility we locate 3.6.4111459
 *  when this version is requested.
 *
 *  Note: cmake --version actually returns "3.6.0-rc2" rather than "3.6.0". Use the less precise
 *  version to avoid giving the impression that fork CMake is not a shipping version in Android
 *  Studio.
 */
internal const val FORK_CMAKE_REPORTED_VERSION = "3.6.0"
internal val forkCmakeReportedVersion = Revision.parseRevision(FORK_CMAKE_REPORTED_VERSION)

/**
 * This is the default version of CMake to use for this Android Gradle Plugin if there was no
 * version defined in build.gradle.
 */
const val DEFAULT_CMAKE_VERSION = "3.18.1"
val defaultCmakeVersion = Revision.parseRevision(DEFAULT_CMAKE_VERSION)
const val DEFAULT_CMAKE_SDK_DOWNLOAD_VERSION = DEFAULT_CMAKE_VERSION

/**
 * This is the probable next CMake to be released or the last CMake released
 * depending on where we are in the process of releasing the next version of CMake.
 * A subset of tests are run against it.
 */
const val OFF_STAGE_CMAKE_VERSION = "3.10.2"

/**
 * @return list of folders (as Files) retrieved from PATH environment variable and from Sdk
 * cmake folder.
 */
private fun getEnvironmentPaths(): List<File> {
    val envPath = System.getenv("PATH") ?: ""
    val pathSeparator = System.getProperty("path.separator").toRegex()
    return envPath
        .split(pathSeparator)
        .asSequence()
        .filter { it.isNotEmpty() }
        .map { File(it) }
        .toList()
}

/**
 * @return list of folders (as Files) for CMakes in the SDK.
 */
private fun getSdkCmakeFolders(sdkRoot : File?) : List<File> {
    return (sdkRoot
            ?.resolve("cmake")
            ?.listFiles()
            ?: arrayOf())
            .map { it.resolve("bin") }
            .filter { it.isDirectory }
            .toList()
}

private fun getSdkCmakePackages(
    androidLocationsProvider: AndroidLocationsProvider,
    sdkFolder: File?
): List<LocalPackage> {
    val androidSdkHandler = AndroidSdkHandler.getInstance(androidLocationsProvider, sdkFolder?.toPath())
    val sdkManager = androidSdkHandler.getSdkManager(
        LoggerProgressIndicatorWrapper(
            ThreadLoggingEnvironment.getILogger(CMAKE_PACKAGES_SDK, CMAKE_PACKAGES_SDK)
        )
    )
    val packages = sdkManager.packages
    return packages.getLocalPackagesForPrefix(FD_CMAKE).toList()
}

private fun getCmakeRevisionFromExecutable(cmakeFolder: File): Revision? {
    if (!cmakeFolder.exists()) {
        return null
    }
    val cmakeExecutableName = if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
        "cmake.exe"
    } else {
        "cmake"
    }
    val cmakeExecutable = File(cmakeFolder, cmakeExecutableName)
    if (!cmakeExecutable.exists()) {
        return null
    }
    return CmakeUtils.getVersion(cmakeFolder)
}

/**
 * Wraps the cmake.version property from build.gradle with useful
 * information about that version.
 */
data class CmakeVersionRequirements(val cmakeVersionFromDsl : String?) {
    /**
     * @return true if the version number has a '+' at the end.
     */
    private val dslVersionHasPlus = cmakeVersionFromDsl?.endsWith("+") ?: false

    /**
     * Computes the version number that is effectively being requested.
     * Handles various error cases where the version couldn't by parsed
     * and returns the default CMake version in those cases.
     *
     */
    val effectiveRequestVersion = computeEffectiveRequestVersion()

    /**
     * @return a human-readable representation of the version that is
     * suitable for using in an error message to the user.
     */
    val humanReadableVersionLanguage =
        when {
            dslVersionHasPlus -> "'$effectiveRequestVersion' or higher"
            else -> "'$effectiveRequestVersion'"
        }

    /**
     * @return true if [version] satisfies [effectiveRequestVersion].
     */
    fun isSatisfiedBy(version:Revision) : Boolean {
        val effectiveCompareVersion =
            if (version.compareTo(
                        forkCmakeSdkVersionRevision, Revision.PreviewComparison.IGNORE) == 0) {
                forkCmakeReportedVersion
            } else version
        return when {
            dslVersionHasPlus ->
                effectiveCompareVersion.compareTo(effectiveRequestVersion, Revision.PreviewComparison.IGNORE) >= 0
            else ->
                effectiveCompareVersion.compareTo(effectiveRequestVersion, Revision.PreviewComparison.IGNORE) == 0
        }
    }

    /**
     * Get the version of CMake to be downloaded. null is returned if there
     * is no possible version to download.
     */
    val downloadVersion = when {
            effectiveRequestVersion.compareTo(forkCmakeReportedVersion, Revision.PreviewComparison.IGNORE) == 0 ->
                FORK_CMAKE_SDK_VERSION
            isSatisfiedBy(defaultCmakeVersion) ->
                DEFAULT_CMAKE_SDK_DOWNLOAD_VERSION
            else -> null
        }

    private fun computeEffectiveRequestVersion() : Revision {
        val withoutPlus = cmakeVersionFromDsl?.trimEnd('+')
        return if (withoutPlus == null) {
            defaultCmakeVersion
        } else {
            try {
                val result = Revision.parseRevision(withoutPlus)
                when {
                    result.major < 3 || (result.major == 3 && result.minor < 6) -> {
                        errorln(
                            CMAKE_VERSION_IS_UNSUPPORTED,
                            "CMake version '$result' is too low. Use 3.7.0 or higher."
                        )
                        defaultCmakeVersion
                    }
                    result.toIntArray(true).size < 3 -> {
                        errorln(
                            CMAKE_VERSION_IS_INVALID,
                            "CMake version '$result' does not have enough precision. Use major.minor.micro in version."
                        )
                        defaultCmakeVersion
                    }
                    else -> result
                }
            } catch (e: NumberFormatException) {
                errorln(
                    CMAKE_VERSION_IS_INVALID,
                    "CMake version '$cmakeVersionFromDsl' is not formatted correctly."
                )
                defaultCmakeVersion
            }
        }
    }
}

/**
 * This is the correct find-path logic that has callback for external dependencies like errors,
 * version of CMake file, and so forth. This is the entry-point for unit testing.
 */
fun findCmakePathLogic(
    cmakeVersionFromDsl: String?,
    cmakePathFromLocalProperties: File?,
    downloader: Consumer<String>?,
    environmentPaths: () -> List<File>,
    sdkFolders: () -> List<File>,
    cmakeVersionGetter: (File) -> Revision?,
    repositoryPackages: () -> List<LocalPackage>
): File? {
    val dsl = CmakeVersionRequirements(cmakeVersionFromDsl)

    fun versionGetter(cmakePath : File) = try {
            cmakeVersionGetter(cmakePath)
        } catch (e: IOException) {
            warnln("Could not execute cmake at '$cmakePath' to get version. Skipping.")
            null
        }

    // List of CMakes that didn't satisfy the version requirement.
    val nonsatisfiers = mutableListOf<String>()

    // If cmake.dir is specified then it's an error if that specific CMake doesn't satisfy the
    // requested version number.
    if (cmakePathFromLocalProperties != null) {
        val version = versionGetter(cmakePathFromLocalProperties.resolve("bin"))
        when {
            version == null ->
                errorln(
                    CMAKE_VERSION_IS_INVALID,
                    "Could not get version from cmake.dir path '$cmakePathFromLocalProperties'."
                )
            cmakeVersionFromDsl == null ->
                // If there is a valid CMake in cmake.dir then don't enforce the default CMake version
                return cmakePathFromLocalProperties
            !dsl.isSatisfiedBy(version) ->
                nonsatisfiers += "'$version' found from cmake.dir"
            else ->
                return cmakePathFromLocalProperties
        }
    }

    val cmakePaths = mutableSetOf<String>()

    // Gather acceptable environment paths
    for (environmentPath in environmentPaths()) {
        if (cmakePaths.contains(environmentPath.path)) continue
        val version = versionGetter(environmentPath) ?: continue
        if (!dsl.isSatisfiedBy(version)) {
            nonsatisfiers += "'$version' found in PATH"
            continue
        }
        cmakePaths.add(environmentPath.path)
    }

    // This is a fallback case for backward compatibility. In the past,
    // we've recommended people manually symlink or copy CMake (and
    // other tools) into SDK folder. Our own integration tests rely on
    // this as well.
    // This logic searches SDK cmake folders in a similar manner to path
    // search. If it finds a non-satisfying CMake, it doesn't record it
    // for the descriptive error message because this is non-standard
    // support and the prior methods should be adequate.
    if (cmakePaths.isEmpty()) {
        for (sdkFolder in sdkFolders()) {
            if (cmakePaths.contains(sdkFolder.path)) continue
            val version = try {
                Revision.parseRevision(keepWhileNumbersAndDots(sdkFolder.parentFile.name))
            } catch (e: Throwable) {
                null
            } ?: versionGetter(sdkFolder) ?: continue
            if (!dsl.isSatisfiedBy(version)) continue
            cmakePaths.add(sdkFolder.path)
        }
    }


    if (cmakePaths.isEmpty()) {
        // Gather acceptable SDK package paths
        for (localPackage in repositoryPackages()) {
            val packagePath = localPackage.location.resolve("bin")
            if (cmakePaths.contains(packagePath.toString())) continue
            val version = if (localPackage.version == forkCmakeSdkVersionRevision) {
                forkCmakeReportedVersion
            } else {
                localPackage.version
            }
            if (!dsl.isSatisfiedBy(version)) {
                nonsatisfiers += "'$version' found in SDK"
                continue
            }
            cmakePaths.add(packagePath.toString())
        }
    }

    // Handle case where there is no match.
    if (cmakePaths.isEmpty()) {
        // If there is a downloader, then try downloading and re-invoke findCmakePathLogic but with
        // no downloader this time.
        if (downloader != null && dsl.downloadVersion != null) {
            downloader.accept(dsl.downloadVersion)
            return findCmakePathLogic(
                cmakeVersionFromDsl,
                cmakePathFromLocalProperties,
                null,
                environmentPaths,
                sdkFolders,
                cmakeVersionGetter,
                repositoryPackages
            )
        }

        // No downloader, so issue error(s)
        errorln(
            CMAKE_IS_MISSING,
            "CMake ${dsl.humanReadableVersionLanguage} was not found in SDK, PATH, or by cmake.dir property."
        )
        nonsatisfiers
            .distinct()
            .onEach {
                errorln(
                    CMAKE_VERSION_IS_INVALID,
                    "- CMake $it did not satisfy requested version."
                )
            }
        return null
    }

    return File(cmakePaths.first()).parentFile
}

/**
 * Whether version is for fork CMake.
 */
fun Revision.isCmakeForkVersion() = major == 3 && minor == 6 && micro == 0

/**
 * Locate CMake cmake path for the given build configuration.
 *
 * cmakeVersionFromDsl is the, possibly null, CMake version from the user's build.gradle.
 *   If it is null then a default version will be chosen.
 */
class CmakeLocator {
    fun findCmakePath(
        cmakeVersionFromDsl: String?,
        cmakeFile: File?,
        androidLocationsProvider: AndroidLocationsProvider,
        sdkFolder: File?,
        downloader: Consumer<String>): File? {
        PassThroughDeduplicatingLoggingEnvironment().use {
            return findCmakePathLogic(
                    cmakeVersionFromDsl,
                    cmakeFile,
                    downloader,
                    { getEnvironmentPaths() },
                    { getSdkCmakeFolders(sdkFolder) },
                    { folder -> getCmakeRevisionFromExecutable(folder) },
                    { getSdkCmakePackages(androidLocationsProvider, sdkFolder) })
        }
    }
}
