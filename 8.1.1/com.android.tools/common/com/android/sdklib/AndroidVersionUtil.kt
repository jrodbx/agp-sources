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
package com.android.sdklib

private const val RO_BUILD_VERSION_SDK = "ro.build.version.sdk"
private const val RO_BUILD_VERSION_CODENAME = "ro.build.version.codename"
private const val BUILD_EXTENSION_PREFIX = "build.version.extensions."

/** [AndroidVersion] related utilities */
object AndroidVersionUtil {

    @JvmStatic
    fun androidVersionFromDeviceProperties(properties: Map<String, String>): AndroidVersion? {
        val apiLevel = properties[RO_BUILD_VERSION_SDK]?.toIntOrNull() ?: return null
        val extensions = properties.filter { it.key.startsWith(BUILD_EXTENSION_PREFIX) }
        // We want to use the extension level of the release that is running on the device.
        // However, for prereleases, we don't have a "build.version.extensions." property
        // corresponding to that release yet. Thus, we just use the extension level of the latest
        // release.
        val extensionLevel = extensions.maxByOrNull { it.key }?.value?.toIntOrNull() ?: 0
        val baseExtensionLevel = AndroidVersion.getBaseExtensionLevel(apiLevel)
        val codename = properties[RO_BUILD_VERSION_CODENAME]
        // We consider preview releases to be base, since we don't necessarily know their base
        // extension level, and when they are released it should be at least the level we see now.
        val isBase = codename != null || extensionLevel <= baseExtensionLevel
        return AndroidVersion(apiLevel, codename, extensionLevel.takeIf { it > 0 }, isBase)

    }
}
