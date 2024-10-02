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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.PrivacySandboxSdkBundle
import com.android.build.api.dsl.PrivacySandboxSdkExtension
import com.android.build.api.dsl.PrivacySandboxSdkOptimization
import org.gradle.api.Action
import com.android.build.api.dsl.SigningConfig

interface InternalPrivacySandboxSdkExtension: PrivacySandboxSdkExtension {
    fun signingConfig(action: Action<SigningConfig>)
    fun bundle(action: Action<PrivacySandboxSdkBundle>)
    fun optimization(action: Action<PrivacySandboxSdkOptimization>)
}
