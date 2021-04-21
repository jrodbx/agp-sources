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
package com.android.ide.common.gradle.model

import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.BuildType
import com.android.builder.model.Variant

interface IdeProductFlavor : IdeBaseConfig {
    /**
     * To learn more about configuring flavor dimensions, read
     * [Combine multiple flavors](https://developer.android.com/studio/build/build-variants.html#flavor-dimensions).
     */
    val dimension: String?

    /**
     * The name of the product flavor. This is only the value set on this product flavor.
     * To get the final application id name, use [AndroidArtifact.getApplicationId].
     */
    val applicationId: String?

    /**
     * The version code associated with this flavor or null if none have been set.
     * This is only the value set on this product flavor, not necessarily the actual
     * version code used.
     */
    val versionCode: Int?

    /** The version name. This is only the value set on this product flavor.
     * To get the final value, use [Variant.getMergedFlavor] with
     * [.getVersionNameSuffix] and [BuildType.getVersionNameSuffix].
     */
    val versionName: String?

    /** The minSdkVersion, or null if not specified. This is only the value set on this product flavor. */
    val minSdkVersion: IdeApiVersion?

    /** The targetSdkVersion, or null if not specified. This is only the value set on this product flavor. */
    val targetSdkVersion: IdeApiVersion?

    /** The maxSdkVersion, or null if not specified. This is only the value set on this produce flavor. */
    val maxSdkVersion: Int?

    /**
     * The test application id. This is only the value set on this product flavor.
     * To get the final value, use [Variant.getExtraAndroidArtifacts] with
     * [AndroidProject.ARTIFACT_ANDROID_TEST] and then
     * [AndroidArtifact.getApplicationId]
     */
    val testApplicationId: String?

    /**
     * The test instrumentation runner. This is only the value set on this product flavor.
     * TODO: make test instrumentation runner available through the model.
     */
    val testInstrumentationRunner: String?

    /** The arguments for the test instrumentation runner.*/
    val testInstrumentationRunnerArguments: Map<String, String>

    /** The handlingProfile value. This is only the value set on this product flavor. */
    val testHandleProfiling: Boolean?

    /** The functionalTest value. This is only the value set on this product flavor. */
    val testFunctionalTest: Boolean?

    /**
     * The resource configuration for this variant.
     *
     * This is the list of -c parameters for aapt.
     */
    val resourceConfigurations: Collection<String>

    val vectorDrawables: IdeVectorDrawablesOptions?
}