/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.api.variant.impl

import com.android.build.api.variant.VariantOutput
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.gradle.internal.scope.ApkData
import org.gradle.api.provider.Property

data class VariantOutputImpl(
    override val versionCode: Property<Int>,
    override val versionName: Property<String>,
    override val isEnabled: Property<Boolean>,
    private val variantOutputConfiguration: VariantOutputConfiguration,
    val apkData: ApkData /* remove once all tasks started using public API to load output.json */
) : VariantOutput, VariantOutputConfiguration by variantOutputConfiguration