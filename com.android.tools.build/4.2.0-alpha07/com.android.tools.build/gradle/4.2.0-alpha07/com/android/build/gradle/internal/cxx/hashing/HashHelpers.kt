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

package com.android.build.gradle.internal.cxx.hashing

import com.android.Version
import com.android.build.gradle.internal.cxx.json.jsonStringOf
import java.security.MessageDigest

/**
 * Compute a hash of the value. Output base 36 similar to git style.
 * By default, includes the current gradle version in the hash since that's safer.
 */
fun <T> sha256Of(value : T, includeGradleVersionInHash : Boolean = true) : String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(jsonStringOf(value))
    if (includeGradleVersionInHash) digest.update(Version.ANDROID_GRADLE_PLUGIN_VERSION)
    return digest.toBase36()
}

/**
 * Compute a hash of the value. Output first few chars base 36 similar.
 */
fun <T> shortSha256Of(value : T, includeGradleVersionInHash : Boolean = true) =
    sha256Of(value, includeGradleVersionInHash).substring(0, 8)