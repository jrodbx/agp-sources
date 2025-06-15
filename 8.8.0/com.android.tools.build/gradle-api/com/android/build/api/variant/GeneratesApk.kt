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

package com.android.build.api.variant

import org.gradle.api.Incubating
import org.gradle.api.provider.Provider

/**
 * Build-time properties for [Component] that produce APKs.
 */
interface GeneratesApk {

    /**
     * Variant's application ID as present in the final manifest file of the APK.
     */
    val applicationId: Provider<String>

    /**
     * Variant's android resources processing configuration, initialized by the corresponding
     * global DSL element.
     */
    val androidResources: AndroidResources

    /**
     * Variant's packagingOptions, initialized by the corresponding global DSL element.
     */
    val packaging: ApkPackaging

    /**
     * Variant specific settings for the renderscript compiler. This will return null when
     * [com.android.build.api.dsl.BuildFeatures.renderScript] is false.
     */
    val renderscript: Renderscript?

    /**
     * Min SDK version for this variant
     */
    @get:Incubating
    val minSdk: AndroidVersion

    /**
     * Target SDK version for this variant.
     */
    val targetSdk: AndroidVersion

    // b/368604254
    @Deprecated(message = "Replaced with targetSdk, will be removed in 9.0", replaceWith = ReplaceWith("targetSdk"))
    val targetSdkVersion: AndroidVersion

    /**
     * Variant's specific dexing options
     */
    @get:Incubating
    val dexing: Dexing

}
