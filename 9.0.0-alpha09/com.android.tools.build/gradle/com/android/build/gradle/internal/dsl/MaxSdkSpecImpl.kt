/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.MaxSdkSpec
import com.android.build.api.dsl.MaxSdkVersion
import com.android.build.gradle.internal.services.DslServices
import javax.inject.Inject

abstract class MaxSdkSpecImpl @Inject constructor(dslService: DslServices) : MaxSdkSpec {

    override fun release(version: Int): MaxSdkVersion {
        return MaxSdkVersionImpl(apiLevel = version)
    }
}

internal data class MaxSdkVersionImpl(override val apiLevel: Int): MaxSdkVersion
