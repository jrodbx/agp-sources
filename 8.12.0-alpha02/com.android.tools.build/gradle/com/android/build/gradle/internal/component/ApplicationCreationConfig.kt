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
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_SHRUNK_RESOURCES_PROTO_FORMAT
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.REVERSE_METADATA_LINKED_RESOURCES_PROTO_FORMAT

interface ApplicationCreationConfig: ApkCreationConfig, VariantCreationConfig, PublishableCreationConfig {
    val profileable: Boolean

    /**
     * Whether this application has shrinking enabled AND has dynamic features.
     *
     * This property is needed because under the above condition, AGP has a different pipeline for
     * publishing/consuming artifacts between the base module and dynamic feature modules.
     *
     * For example, the pipeline for resource shrinking is as follows:
     *   1. From dynamic features, publish [REVERSE_METADATA_LINKED_RESOURCES_PROTO_FORMAT]
     *   2. From base, consume [REVERSE_METADATA_LINKED_RESOURCES_PROTO_FORMAT], process it, and
     *   publish [FEATURE_SHRUNK_RESOURCES_PROTO_FORMAT]
     *   3. From dynamic features, consume [FEATURE_SHRUNK_RESOURCES_PROTO_FORMAT]
     */
    val shrinkingWithDynamicFeatures: Boolean

    val needAssetPackTasks: Boolean
    val isWearAppUnbundled: Boolean?
    val includeVcsInfo: Boolean?
    override val bundleConfig: BundleConfigImpl
    override val androidResources: ApplicationAndroidResourcesImpl
    val outputs: VariantOutputList

    fun addVariantOutput(variantOutputConfiguration: VariantOutputConfiguration)
}
