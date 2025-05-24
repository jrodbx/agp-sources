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

package com.android.build.gradle.internal.scope

/**
 * Allows access to the final values of
 * [com.android.build.api.dsl.ApplicationBuildFeatures]
 * [com.android.build.api.dsl.DynamicFeatureBuildFeatures]
 * [com.android.build.api.dsl.LibraryBuildFeatures]
 * [com.android.build.api.dsl.TestBuildFeatures]
 *
 * This is a union of all above interfaces to simplify things internally.
 *
 * The values returned take into account default values coming via
 * [com.android.build.gradle.options.BooleanOption]
 */
interface BuildFeatureValues {
    // ------------------
    // Common flags

    val aidl: Boolean
    val compose: Boolean
    val buildConfig: Boolean
    val dataBinding: Boolean
    val mlModelBinding: Boolean
    val prefab: Boolean
    val renderScript: Boolean
    val resValues: Boolean
    val shaders: Boolean
    val viewBinding: Boolean

    // ------------------
    // Application flags

    // ------------------
    // Dynamic-Feature flags

    // ------------------
    // Library flags

    val buildType: Boolean
    val androidResources: Boolean
    val prefabPublishing: Boolean

    // ------------------
    // Test flags
}