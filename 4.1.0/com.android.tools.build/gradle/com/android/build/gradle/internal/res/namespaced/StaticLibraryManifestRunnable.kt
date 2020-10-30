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

package com.android.build.gradle.internal.res.namespaced

import java.io.File
import java.io.Serializable
import java.nio.charset.StandardCharsets
import javax.inject.Inject

class StaticLibraryManifestRunnable @Inject constructor(
        val params: StaticLibraryManifestRequest) : Runnable {
    override fun run() {
        params.manifestFile.outputStream().writer(StandardCharsets.UTF_8).buffered().use {
            it.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                    .append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n")
                    .append("    package=\"${params.packageName}\"/>\n")
        }
    }
}

data class StaticLibraryManifestRequest(
        val manifestFile: File,
        val packageName: String) : Serializable
