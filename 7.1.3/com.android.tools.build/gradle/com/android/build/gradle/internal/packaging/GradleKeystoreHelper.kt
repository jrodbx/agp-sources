/*
 * Copyright (C) 2017 The Android Open Source Project
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
@file:JvmName("GradleKeystoreHelper")

package com.android.build.gradle.internal.packaging

import com.android.build.gradle.internal.LoggerWrapper
import com.android.builder.signing.DefaultSigningConfig
import com.android.ide.common.signing.KeystoreHelper
import com.android.ide.common.signing.KeytoolException
import com.android.prefs.AndroidLocationsProvider
import org.gradle.api.InvalidUserDataException
import org.gradle.api.logging.Logger
import shadow.bundletool.com.android.prefs.AndroidLocation.AndroidLocationException
import java.io.File
import java.io.IOException

fun AndroidLocationsProvider.getDefaultDebugKeystoreLocation(): File = try {
    KeystoreHelper.defaultDebugKeystoreLocation(this)
} catch (e: AndroidLocationException) {
    throw InvalidUserDataException("Failed to get default debug keystore location.", e)
}

@Throws(IOException::class)
fun createDefaultDebugStore(defaultDebugKeystoreLocation: File, logger: Logger) {
    val signingConfig = DefaultSigningConfig.DebugSigningConfig(defaultDebugKeystoreLocation)
    logger.info(
            "Creating default debug keystore at {}",
            defaultDebugKeystoreLocation.absolutePath)
    try {
        if (!KeystoreHelper.createDebugStore(
                signingConfig.storeType,
                signingConfig.storeFile,
                signingConfig.storePassword,
                signingConfig.keyPassword,
                signingConfig.keyAlias,
                LoggerWrapper(logger))) {
            throw IOException("Unable to create missing debug keystore.")
        }
    } catch (e: KeytoolException) {
        throw IOException(e)
    }
}
