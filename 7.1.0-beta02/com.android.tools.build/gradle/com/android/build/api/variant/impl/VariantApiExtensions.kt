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

package com.android.build.api.variant.impl

import com.android.build.api.variant.AndroidVersion

/**
 * Returns the API level as an integer. If this is a preview platform, it
 * will return the expected final version of the API rather than the current API
 * level. This is the "feature level" as opposed to the "release level" returned by
 * [.getApiLevel] in the sense that it is useful when you want
 * to check the presence of a given feature from an API, and we consider the feature
 * present in preview platforms as well.
 *
 * @return the API level of this version, +1 for preview platforms
 */
fun AndroidVersion.getFeatureLevel(): Int =
    if (codename != null) apiLevel + 1 else apiLevel

/**
 * Returns a string representing the API level and/or the code name.
 */
fun AndroidVersion.getApiString(): String = codename ?: apiLevel.toString()

/**
 * Convert public API [AndroidVersion] to one used by the model :
 * [com.android.sdklib.AndroidVersion]
 */
fun AndroidVersion.toSharedAndroidVersion(): com.android.sdklib.AndroidVersion {
    return com.android.sdklib.AndroidVersion(apiLevel, codename)
}
