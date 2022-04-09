/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal.ndk

import com.android.build.gradle.internal.cxx.configure.NdkLocator
import com.android.build.gradle.internal.cxx.configure.NdkLocatorRecord
import com.android.build.gradle.internal.ndk.NdkInstallStatus.Invalid
import com.android.build.gradle.internal.ndk.NdkInstallStatus.NotInstalled
import com.android.build.gradle.internal.ndk.NdkInstallStatus.Valid
import org.gradle.api.InvalidUserDataException

sealed class NdkInstallStatus {
    /**
     * Indicates a valid NDK install.
     */
    class Valid(val platform: NdkPlatform) : NdkInstallStatus()

    /**
     * Indicates an installed but invalid NDK.
     *
     * An NDK is invalid if the directory exists but is otherwise determined to be not usable. We
     * may have been unable to determine the NDK's revision, it could be an unsupported NDK, or the
     * NDK-specific validation may have failed (commonly because the install was corrupt and was
     * missing necessary components).
     *
     * The contained error message describes the detected failure.
     */
    class Invalid(val errorMessage: String) : NdkInstallStatus()

    /**
     * Indicates a missing NDK install.
     *
     * If the NDK directory was not able to be determined or the directory does not exist, the NDK
     * has not yet been installed.
     */
    object NotInstalled : NdkInstallStatus()

    val isConfigured: Boolean
        get() {
            return this is Valid
        }

    fun getOrThrow(): NdkPlatform {
        when (this) {
            is Valid -> return platform
            is Invalid -> throw InvalidUserDataException(errorMessage)
            is NotInstalled -> throw InvalidUserDataException("NDK is not installed")
        }
    }
}

/**
 * Handles NDK related information.
 */
open class NdkHandler(
    private val ndkLocator: NdkLocator,
    private val compileSdkVersion: String
) {
    private var ndkInstallStatus: NdkInstallStatus? = null

    private fun getNdkInfo(ndk: NdkLocatorRecord) = when {
        ndk.revision.major >= 21 -> NdkR21Info(ndk.ndk)
        ndk.revision.major >= 19 -> NdkR19Info(ndk.ndk)
        ndk.revision.major >= 18 -> NdkR18Info(ndk.ndk)
        ndk.revision.major >= 17 -> NdkR17Info(ndk.ndk)
        ndk.revision.major >= 14 -> NdkR14Info(ndk.ndk)
        else -> DefaultNdkInfo(ndk.ndk)
    }

    private fun getNdkStatus(downloadOkay: Boolean): NdkInstallStatus {
        val ndk = ndkLocator.findNdkPath(downloadOkay)
            ?: return NotInstalled
        val ndkInfo = getNdkInfo(ndk)
        val error = ndkInfo.validate()
        if (error != null) return Invalid(error)
        return Valid(NdkPlatform(ndk.ndk, ndkInfo, ndk.revision, compileSdkVersion))
    }

    fun getNdkPlatform(downloadOkay: Boolean) : NdkInstallStatus {
        if (ndkInstallStatus == null ||
            (downloadOkay && ndkInstallStatus == NotInstalled)) {
            // Calculate NDK platform if that hadn't been done before or if it's
            // okay to download now.
            ndkInstallStatus = getNdkStatus(downloadOkay)
        }
        return ndkInstallStatus!!
    }

    val ndkPlatform: NdkInstallStatus
        get() = getNdkPlatform(downloadOkay = false)
}
