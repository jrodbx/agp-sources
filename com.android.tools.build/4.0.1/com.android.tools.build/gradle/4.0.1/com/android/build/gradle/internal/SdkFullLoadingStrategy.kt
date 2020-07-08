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

import com.android.builder.internal.compiler.RenderScriptProcessor
import com.android.builder.sdk.SdkInfo
import com.android.builder.sdk.TargetInfo
import com.android.repository.Revision
import com.android.sdklib.BuildToolInfo
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.OptionalLibrary
import java.io.File
import java.util.function.Supplier

class SdkFullLoadingStrategy(
    private val sdkHandlerSupplier: Supplier<SdkHandler>,
    private val platformTargetHashSupplier: Supplier<String?>,
    private val buildToolRevisionSupplier: Supplier<Revision?>,
    private val useAndroidX: Boolean) {

    private var sdkInitResult: Boolean? = null
    private lateinit var sdkInfo: SdkInfo
    private lateinit var targetInfo: TargetInfo

    /**
     * Initializes the backing SDK Handler and return true/false if the initialization was successful or not.
     */
    @Synchronized
    private fun init(): Boolean {
        if (sdkInitResult != null) return sdkInitResult!!
        val platformHash = checkNotNull(platformTargetHashSupplier.get()) {
            "Extension not initialized yet, couldn't access compileSdkVersion."}
        val buildToolRevision = checkNotNull(buildToolRevisionSupplier.get()) {
            "Extension not initialized yet, couldn't access buildToolsVersion."}

        val sdkHandler = sdkHandlerSupplier.get()

        val result = sdkHandler.initTarget(platformHash, buildToolRevision)
        if (result == null) {
            sdkInitResult = false
            return false
        }
        sdkHandler.ensurePlatformToolsIsInstalledWarnOnFailure()
        sdkInitResult = true
        sdkInfo = result.first
        targetInfo = result.second
        return true
    }

    fun getAdbExecutable() = if (init()) sdkInfo.adb else null
    fun getAnnotationsJar() = if (init()) sdkInfo.annotationsJar else null

    private fun getFileFromTarget(component: Int) = if (init()) targetInfo.target.getFile(component) else null
    fun getAidlFramework() = getFileFromTarget(IAndroidTarget.ANDROID_AIDL)
    fun getAndroidJar() = getFileFromTarget(IAndroidTarget.ANDROID_JAR)
    fun getAdditionalLibraries(): List<OptionalLibrary>? = if (init()) targetInfo.target.additionalLibraries else null
    fun getOptionalLibraries(): List<OptionalLibrary>? = if (init()) targetInfo.target.optionalLibraries else null
    fun getTargetPlatformVersion() = if (init()) targetInfo.target.version else null
    fun getTargetBootClasspath() = if (init()) targetInfo.target.bootClasspath.map { File(it) } else null


    fun getBuildToolsInfo() = if (init()) targetInfo.buildTools else null
    fun getBuildToolsRevision() = getBuildToolsInfo()?.revision

    private fun getFileFromBuildTool(component: BuildToolInfo.PathId) =
        getBuildToolsInfo()?.let { File(it.getPath(component)) }

    fun getAidlExecutable() = getFileFromBuildTool(BuildToolInfo.PathId.AIDL)
    fun getCoreLambaStubs() = getFileFromBuildTool(BuildToolInfo.PathId.CORE_LAMBDA_STUBS)
    fun getSplitSelectExecutable() = getFileFromBuildTool(BuildToolInfo.PathId.SPLIT_SELECT)

    fun getRenderScriptSupportJar() = getBuildToolsInfo()?.let {
        RenderScriptProcessor.getSupportJar(it.location, useAndroidX)
    }

    fun getSupportNativeLibFolder() = getBuildToolsInfo()?.let {
        RenderScriptProcessor.getSupportNativeLibFolder(it.location)
    }

    fun getSupportBlasLibFolder() = getBuildToolsInfo()?.let {
        RenderScriptProcessor.getSupportBlasLibFolder(it.location)
    }

    @Synchronized
    fun reset() {
        sdkInitResult = null
        sdkHandlerSupplier.get().unload()
    }
}
