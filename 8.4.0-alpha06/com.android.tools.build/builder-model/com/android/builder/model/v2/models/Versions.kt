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

package com.android.builder.model.v2.models

import com.android.builder.model.v2.AndroidModel

/**
 * Basic model providing version information about the actual models.
 *
 * This model is meant to be very stable and never change, so that Studio can safely query it.
 */
interface Versions: AndroidModel {
    interface Version {
        val major: Int
        val minor: Int
        /**
         * An optional, human-readable, representation of this version
         *
         * For example, minimum model consumer version 64.1 might correspond to
         * Android Studio Giraffe patch 1.
         */
        val humanReadable: String?
    }

    val versions: Map<String, Version>

    companion object {
        const val BASIC_ANDROID_PROJECT = "basic_android_project"
        const val ANDROID_PROJECT = "android_project"
        const val ANDROID_DSL = "android_dsl"
        const val VARIANT_DEPENDENCIES = "variant_dependencies"
        const val NATIVE_MODULE = "native_module"
        /**
         * The minimum required model consumer version, to allow AGP to drop support for older versions
         * of Android Studio.
         *
         * (Android Studio's model consumer version will be incremented after each release branching)
         *
         * If not present, a future version of AGP will not be considered compatible
         */
        const val MINIMUM_MODEL_CONSUMER = "minimum_model_consumer"
    }

    /**
     * The version of AGP.
     */
    val agp: String
}
