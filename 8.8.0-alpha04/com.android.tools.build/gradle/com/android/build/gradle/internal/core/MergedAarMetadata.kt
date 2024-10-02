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
package com.android.build.gradle.internal.core

import com.android.build.api.dsl.AarMetadata

/** Used to merge multiple instances of [AarMetadata] together.  */
class MergedAarMetadata : MergedOptions<AarMetadata>, AarMetadata {

    override var minCompileSdk: Int? = null
    override var minCompileSdkExtension: Int? = null
    override var minAgpVersion: String? = null

    override fun reset() {
        minCompileSdk = null
        minCompileSdkExtension = null
        minAgpVersion = null
    }

    override fun append(option: AarMetadata) {
        option.minCompileSdk?.let { minCompileSdk = it }
        option.minCompileSdkExtension?.let { minCompileSdkExtension = it }
        option.minAgpVersion?.let { minAgpVersion = it }
    }

    fun append(option: MergedAarMetadata) {
        option.minCompileSdk?.let { minCompileSdk = it }
        option.minCompileSdkExtension?.let { minCompileSdkExtension = it }
        option.minAgpVersion?.let { minAgpVersion = it }
    }
}
