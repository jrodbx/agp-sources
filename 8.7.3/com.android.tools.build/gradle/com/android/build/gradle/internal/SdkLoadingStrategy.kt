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

import com.android.repository.Revision
import com.android.sdklib.BuildToolInfo
import java.io.File

class SdkLoadingStrategy(
    private val directLoad: SdkDirectLoadingStrategy,
    private val fullLoad: SdkFullLoadingStrategy) {

    fun getAidlExecutable() = if (directLoad.loadedSuccessfully()) directLoad.getAidlExecutable() else fullLoad.getAidlExecutable()
    fun getAidlFramework() = if (directLoad.loadedSuccessfully()) directLoad.getAidlFramework() else fullLoad.getAidlFramework()
    fun getAdbExecutable() = if (directLoad.loadedSuccessfully()) directLoad.getAdbExecutable() else fullLoad.getAdbExecutable()
    fun getAndroidJar() = if (directLoad.loadedSuccessfully()) directLoad.getAndroidJar() else fullLoad.getAndroidJar()
    fun getAnnotationsJar() = if (directLoad.loadedSuccessfully()) directLoad.getAnnotationsJar() else fullLoad.getAnnotationsJar()
    fun getAdditionalLibraries() = if (directLoad.loadedSuccessfully()) directLoad.getAdditionalLibraries() else fullLoad.getAdditionalLibraries()
    fun getOptionalLibraries() = if (directLoad.loadedSuccessfully()) directLoad.getOptionalLibraries() else fullLoad.getOptionalLibraries()
    fun getApiVersionsFile(): File? = if (directLoad.loadedSuccessfully()) directLoad.getApiVersionsFile() else fullLoad.getApiVersionsFile()
    fun getTargetPlatformVersion() = if (directLoad.loadedSuccessfully()) directLoad.getTargetPlatformVersion() else fullLoad.getTargetPlatformVersion()
    fun getTargetBootClasspath() = if (directLoad.loadedSuccessfully()) directLoad.getTargetBootClasspath() else fullLoad.getTargetBootClasspath()

    fun getBuildToolsInfo(): BuildToolInfo? = if (directLoad.loadedSuccessfully()) directLoad.getBuildToolsInfo() else fullLoad.getBuildToolsInfo()
    fun getBuildToolsRevision(): Revision? = if (directLoad.loadedSuccessfully()) directLoad.getBuildToolsRevision() else fullLoad.getBuildToolsRevision()
    fun getSplitSelectExecutable(): File? = if (directLoad.loadedSuccessfully()) directLoad.getSplitSelectExecutable() else fullLoad.getSplitSelectExecutable()
    fun getCoreLambaStubs(): File? = if (directLoad.loadedSuccessfully()) directLoad.getCoreLambaStubs() else fullLoad.getCoreLambaStubs()

    fun getRenderScriptSupportJar(): File? = if (directLoad.loadedSuccessfully()) directLoad.getRenderScriptSupportJar() else fullLoad.getRenderScriptSupportJar()
    fun getSupportNativeLibFolder(): File? = if (directLoad.loadedSuccessfully()) directLoad.getSupportNativeLibFolder() else fullLoad.getSupportNativeLibFolder()
    fun getSupportBlasLibFolder(): File? = if (directLoad.loadedSuccessfully()) directLoad.getSupportBlasLibFolder() else fullLoad.getSupportBlasLibFolder()

    fun getSystemImageLibFolder(imageHash: String): File? =
        directLoad.getSystemImageLibFolder(imageHash)?: fullLoad.getSystemImageLibFolder(imageHash)

    fun getEmulatorLibFolder(): File? =
        directLoad.getEmulatorLibFolder()?: fullLoad.getEmulatorLibFolder()

    fun getCoreForSystemModulesJar(): File? = if (directLoad.loadedSuccessfully()) directLoad.getCoreForSystemModulesJar() else fullLoad.getCoreForSystemModulesJar()

    @Synchronized
    fun reset() {
        directLoad.reset()
        fullLoad.reset()
    }
}
