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

import com.android.builder.model.v2.dsl.BuildType
import com.android.builder.model.v2.dsl.DependenciesInfo
import com.android.builder.model.v2.dsl.ProductFlavor
import com.android.builder.model.v2.dsl.SigningConfig
import com.android.builder.model.v2.ide.AaptOptions
import com.android.builder.model.v2.ide.LintOptions
import com.android.builder.model.v2.models.AndroidDsl
import java.io.Serializable

data class AndroidDslImpl(
    override val groupId: String?,
    override val defaultConfig: ProductFlavor,
    override val buildTypes: List<BuildType>,
    override val flavorDimensions: Collection<String>,
    override val productFlavors: List<ProductFlavor>,
    override val compileTarget: String,
    override val signingConfigs: Collection<SigningConfig>,
    override val aaptOptions: AaptOptions,
    override val lintOptions: LintOptions,
    override val buildToolsVersion: String,
    override val dependenciesInfo: DependenciesInfo?
): AndroidDsl, Serializable {
    companion object {
        @JvmStatic
        private val serialVersionUID: Long = 1L
    }
}
