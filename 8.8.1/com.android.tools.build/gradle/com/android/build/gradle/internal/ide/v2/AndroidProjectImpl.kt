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

import com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags
import com.android.builder.model.v2.ide.JavaCompileOptions
import com.android.builder.model.v2.ide.Variant
import com.android.builder.model.v2.ide.ViewBindingOptions
import com.android.builder.model.v2.models.AndroidProject
import java.io.File
import java.io.Serializable

/**
 * Implementation of [AndroidProject] for serialization via the Tooling API.
 */
data class AndroidProjectImpl(
    override val namespace: String,
    override val androidTestNamespace: String?,
    override val testFixturesNamespace: String?,
    override val variants: Collection<Variant>,
    override val javaCompileOptions: JavaCompileOptions,
    override val resourcePrefix: String?,
    override val dynamicFeatures: Collection<String>?,
    override val viewBindingOptions: ViewBindingOptions?,
    override val flags: AndroidGradlePluginProjectFlags,
    override val lintChecksJars: List<File>,
    override val desugarLibConfig: List<File>,
    override val lintJar: File?
) : AndroidProject, Serializable {
    companion object {
        @JvmStatic
        private val serialVersionUID: Long = 1L
    }
}
