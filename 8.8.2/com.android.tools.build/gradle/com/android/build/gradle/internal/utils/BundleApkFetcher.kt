/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.internal.utils

import com.android.build.gradle.internal.tasks.extractApkFilesBypassingBundleTool
import com.android.bundle.Devices
import java.io.File
import java.nio.file.Path

interface BundleApkFetcher {
    fun getApkFiles(
        apkBundles: Collection<Path>,
        deviceSpec: Devices.DeviceSpec
    ): List<Path> {
        return com.android.build.gradle.internal.tasks.getApkFiles(apkBundles, deviceSpec)
    }

    fun getPrivacySandboxSdkApkFiles(apk: Path): List<File> {
        return extractApkFilesBypassingBundleTool(apk).map {it.toFile()}
    }
}
