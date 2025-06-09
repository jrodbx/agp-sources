/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.builder.model.v2.dsl.BuildType
import com.android.builder.model.v2.dsl.ClassField
import java.io.File
import java.io.Serializable

/**
 * Implementation of [BuildType] for serialization via the Tooling API.
 */
data class BuildTypeImpl(
    override val isDebuggable: Boolean,
    override val isProfileable: Boolean,
    override val isTestCoverageEnabled: Boolean,
    override val isPseudoLocalesEnabled: Boolean,
    override val isJniDebuggable: Boolean,
    override val isRenderscriptDebuggable: Boolean,
    override val renderscriptOptimLevel: Int,
    override val isMinifyEnabled: Boolean,
    override val isZipAlignEnabled: Boolean,
    override val isEmbedMicroApp: Boolean,
    override val signingConfig: String?,
    override val applicationIdSuffix: String?,
    override val versionNameSuffix: String?,
    override val buildConfigFields: Map<String, ClassField>?,
    override val resValues: Map<String, ClassField>?,
    override val proguardFiles: Collection<File>,
    override val consumerProguardFiles: Collection<File>,
    override val testProguardFiles: Collection<File>,
    override val manifestPlaceholders: Map<String, Any>,
    override val multiDexEnabled: Boolean?,
    override val multiDexKeepFile: File?,
    override val multiDexKeepProguard: File?,
    override val isDefault: Boolean? = null,
    override val name: String
) : BuildType, Serializable {
    companion object {
        @JvmStatic
        private val serialVersionUID: Long = 1L
    }
}
