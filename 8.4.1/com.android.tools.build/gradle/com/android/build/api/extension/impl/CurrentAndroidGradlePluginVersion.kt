/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.api.extension.impl

import com.android.build.api.AndroidPluginVersion
import com.android.ide.common.repository.AgpVersion
import com.google.common.annotations.VisibleForTesting
import java.util.Properties

object CurrentAndroidGradlePluginVersion {
    @VisibleForTesting
    fun loadAndroidGradlePluginVersions(): String {
            return CurrentAndroidGradlePluginVersion::class.java
                .getResourceAsStream("version.properties")
                .buffered().use { stream ->
                    Properties().let { properties ->
                        properties.load(stream)
                        properties.getProperty("buildVersion")
                    }
                }
    }

    @VisibleForTesting
    internal fun parseAndroidGradlePluginVersion(version: String): AndroidPluginVersion {
        val parsed = AgpVersion.parse(version)
        val stable = AndroidPluginVersion(parsed.major, parsed.minor, parsed.micro)
        if (version.endsWith("-dev")) {
            return stable.dev()
        }
        return when(parsed.previewType) {
            null -> stable
            "alpha" -> stable.alpha(parsed.preview!!)
            "beta" -> stable.beta(parsed.preview!!)
            "rc" -> stable.rc(parsed.preview!!)
            else -> throw throw IllegalStateException("Internal error: Unexpected Android Gradle Plugin version: $version: ${parsed.previewType} is expected to be 'alpha', 'beta' or 'rc'.")
        }
    }

    @JvmField
    val CURRENT_AGP_VERSION: AndroidPluginVersion = parseAndroidGradlePluginVersion(loadAndroidGradlePluginVersions())
}
