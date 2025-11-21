/*
 * Copyright (C) 2022 The Android Open Source Project
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
 * We don't expose public build features dsl, however we use this class internally to indicate
 * whether a component supports a certain feature. For kmp, we support only compiling sources
 * so all feature values are overridden to false.
 */
class KotlinMultiplatformBuildFeaturesValuesImpl(
    override val androidResources: Boolean = false
): BuildFeatureValues {

    override val aidl: Boolean = false
    override val buildConfig: Boolean = false
    override val prefab: Boolean = false
    override val renderScript: Boolean = false
    override val shaders: Boolean = false
    override val prefabPublishing: Boolean = false
    override val compose: Boolean = false
    override val dataBinding: Boolean = false
    override val mlModelBinding: Boolean = false
    override val resValues: Boolean = false
    override val viewBinding: Boolean = false
    override val buildType: Boolean = false
}
