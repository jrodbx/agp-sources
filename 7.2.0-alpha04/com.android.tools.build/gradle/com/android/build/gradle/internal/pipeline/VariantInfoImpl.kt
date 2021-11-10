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

package com.android.build.gradle.internal.pipeline

import com.android.build.api.variant.VariantInfo
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.utils.toImmutableList
import com.google.common.collect.ImmutableList

data class VariantInfoImpl(
    val _isTest: Boolean,
    val _variantName: String,
    val _buildTypeName: String?,
    val _flavorNames: ImmutableList<String>,
    val _isDebuggable: Boolean
) : VariantInfo {

    constructor(creationConfig: VariantCreationConfig) :
            this(
                _isTest = creationConfig.variantType.isForTesting,
                _variantName = creationConfig.name,
                _buildTypeName = creationConfig.buildType,
                _flavorNames = creationConfig.productFlavors.map { it.second }.toImmutableList(),
                _isDebuggable = creationConfig.debuggable
            )

    override fun isTest(): Boolean = _isTest
    override fun getFullVariantName(): String = _variantName
    override fun getBuildTypeName(): String = _buildTypeName ?: ""
    override fun getFlavorNames(): ImmutableList<String> = _flavorNames
    override fun isDebuggable(): Boolean = _isDebuggable

}
