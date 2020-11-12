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

import com.android.builder.model.v2.dsl.DependenciesInfo
import com.android.builder.model.v2.dsl.SigningConfig
import com.android.builder.model.v2.ide.AaptOptions
import com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags
import com.android.builder.model.v2.ide.BuildTypeContainer
import com.android.builder.model.v2.ide.JavaCompileOptions
import com.android.builder.model.v2.ide.LintOptions
import com.android.builder.model.v2.ide.ProductFlavorContainer
import com.android.builder.model.v2.ide.ProjectType
import com.android.builder.model.v2.ide.Variant
import com.android.builder.model.v2.ide.ViewBindingOptions
import com.android.builder.model.v2.models.AndroidProject
import java.io.File
import java.io.Serializable

/**
 * Implementation of [AndroidProject] for serialization via the Tooling API.
 */
data class AndroidProjectImpl(
    override val path: String,
    override val projectType: ProjectType,
    override val groupId: String?,
    override val defaultConfig: ProductFlavorContainer,
    override val buildTypes: Collection<BuildTypeContainer>,
    override val productFlavors: Collection<ProductFlavorContainer>,
    override val variants: Collection<Variant>,
    override val flavorDimensions: Collection<String>,
    override val compileTarget: String,
    override val bootClasspath: Collection<File>,
    override val signingConfigs: Collection<SigningConfig>,
    override val aaptOptions: AaptOptions,
    override val lintOptions: LintOptions,
    override val javaCompileOptions: JavaCompileOptions,
    override val buildFolder: File,
    override val resourcePrefix: String?,
    override val buildToolsVersion: String,
    override val dynamicFeatures: Collection<String>?,
    override val viewBindingOptions: ViewBindingOptions?,
    override val dependenciesInfo: DependenciesInfo?,
    override val flags: AndroidGradlePluginProjectFlags,
    override val lintRuleJars: List<File>
) : AndroidProject, Serializable {
    companion object {
        @JvmStatic
        private val serialVersionUID: Long = 1L
    }
}
