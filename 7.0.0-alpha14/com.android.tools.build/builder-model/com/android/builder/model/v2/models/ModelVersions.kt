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
 * Basic model providing version information about the actual model.
 *
 * This model is meant to be very stable and never change, so that Studio can safely query it.
 */
interface ModelVersions: AndroidModel {
    interface Version {
        val major: Int
        val minor: Int
    }

    val androidProject: Version
    val androidDsl: Version
    val variantDependencies: Version
    val nativeModule: Version
}
