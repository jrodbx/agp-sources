/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.ide.common.fonts

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.function.Supplier

/** Provides fonts stored in SDK or mimics it with a temporary folder if SDK is not available. */
class SdkFontsFolderProvider(private val sdkHomeProvider: Supplier<File?>) : FontsFolderProvider {
    private var cachedSdkHome: File? = null
    override val fontsFolder: File?
        get() {
            var sdkHome = sdkHomeProvider.get()
            if (sdkHome == null) {
                sdkHome = cachedSdkHome ?: createTempSdk()
                cachedSdkHome = sdkHome
            }
            return getFontsPath(sdkHome)
        }

    private fun createTempSdk(): File? {
        return try {
            Files.createTempDirectory("tempSdk").toFile()
        } catch (ex: IOException) {
            null
        }
    }
}
