/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.CompileSdkSpec
import com.android.build.api.dsl.CompileSdkReleaseSpec
import com.android.build.api.dsl.CompileSdkVersion
import com.android.build.gradle.internal.services.DslServices
import com.android.sdklib.SdkVersionInfo
import org.gradle.api.Action
import javax.inject.Inject

abstract class CompileSdkSpecImpl @Inject constructor(private val dslService: DslServices): CompileSdkSpec {

    override fun release(version: Int, action: (CompileSdkReleaseSpec.() -> Unit)): CompileSdkVersion {
        val compileSdkRelease = createCompileSdkReleaseSpec()
        action.invoke(compileSdkRelease)

        return CompileSdkVersionImpl(
            apiLevel = version,
            minorApiLevel = compileSdkRelease.minorApiLevel,
            sdkExtension = compileSdkRelease.sdkExtension,
        )
    }

    override fun release(version: Int): CompileSdkVersion {
        return CompileSdkVersionImpl(apiLevel = version)
    }

    fun release(version: Int, action: Action<CompileSdkReleaseSpec>): CompileSdkVersion {
        val compileSdkRelease = createCompileSdkReleaseSpec()
        action.execute(compileSdkRelease)

        return CompileSdkVersionImpl(
            apiLevel = version,
            minorApiLevel = compileSdkRelease.minorApiLevel,
            sdkExtension = compileSdkRelease.sdkExtension,
        )
    }

    private fun createCompileSdkReleaseSpec(): CompileSdkReleaseSpec {
        return dslService.newDecoratedInstance(
            CompileSdkReleaseSpecImpl::class.java, dslService
        )
    }

    override fun preview(codeName: String): CompileSdkVersion {
        val apiLevel = SdkVersionInfo.getApiByBuildCode(codeName, true) - 1
        return CompileSdkVersionImpl(apiLevel = apiLevel, codeName = codeName)
    }

    override fun addon(vendor: String, name: String, version: Int): CompileSdkVersion {
        return CompileSdkVersionImpl(vendorName = vendor, addonName = name, apiLevel = version)
    }
}

internal data class CompileSdkVersionImpl(
    override val apiLevel: Int? = null,
    override val minorApiLevel: Int? = null,
    override val sdkExtension: Int? = null,
    override val codeName: String? = null,
    override val addonName: String? = null,
    override val vendorName: String? = null,
): CompileSdkVersion {
    fun isAddon() = vendorName != null && addonName != null

    // Converts to the string representation of the Android version
    fun toHash(): String? {
        if (codeName != null) {
            return "android-$codeName"
        }
        if (apiLevel == null) {
            return null
        }
        if (isAddon()) {
            return "$vendorName:$addonName:$apiLevel"
        }
        var compileSdkString = "android-$apiLevel"
        if (minorApiLevel != null) {
            compileSdkString += ".$minorApiLevel"
        }
        if (sdkExtension != null) {
            compileSdkString += "-ext$sdkExtension"
        }
        return compileSdkString
    }
}

abstract class CompileSdkReleaseSpecImpl @Inject constructor(
    dslService: DslServices,
) : CompileSdkReleaseSpec
