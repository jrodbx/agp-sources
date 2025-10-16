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

package com.android.build.gradle.internal.ide.v2

import com.android.builder.model.v2.models.Versions
import com.android.builder.model.v2.models.Versions.Version
import java.io.Serializable

data class VersionsImpl(
    override val agp: String,
    override val versions: Map<String, Version>,
): Versions, Serializable {
    companion object {
        @JvmStatic
        private val serialVersionUID: Long = 1L
    }
}

data class VersionImpl(
    override val major: Int,
    override val minor: Int,
    override val humanReadable: String? = null,
): Version, Serializable {
    companion object {
        @JvmStatic
        private val serialVersionUID: Long = 1L
    }
}
