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

package com.android.build.gradle.internal.component

import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.api.variant.impl.ApplicationAndroidResourcesImpl
import com.android.build.api.variant.impl.BundleConfigImpl
import com.android.build.api.variant.impl.VariantOutputList

interface ApplicationCreationConfig: ApkCreationConfig, VariantCreationConfig, PublishableCreationConfig {
    val profileable: Boolean

    /**
     * Whether this is the base module in a bundle, and the base module needs to consume dynamic
     * feature modules (i.e., the bundle has dynamic features and shrinking is enabled).
     *
     * This property is needed because under the above condition, AGP has a different pipeline for
     * publishing/consuming artifacts between the base module and the dynamic feature modules.
     */
    val consumesDynamicFeatures: Boolean

    val needAssetPackTasks: Boolean
    val isWearAppUnbundled: Boolean?
    val includeVcsInfo: Boolean?
    override val bundleConfig: BundleConfigImpl
    override val androidResources: ApplicationAndroidResourcesImpl
    val outputs: VariantOutputList

    fun addVariantOutput(variantOutputConfiguration: VariantOutputConfiguration)
}
