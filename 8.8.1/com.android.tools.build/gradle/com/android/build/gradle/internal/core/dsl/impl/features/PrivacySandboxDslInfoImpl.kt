/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.gradle.internal.core.dsl.impl.features

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.DynamicFeatureExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.TestExtension
import com.android.build.gradle.internal.core.dsl.features.PrivacySandboxDslInfo

class PrivacySandboxDslInfoImpl(private val extension: CommonExtension<*,*,*,*,*,*>): PrivacySandboxDslInfo {
    override val enable: Boolean
        get() {
            return when(extension) {
                is ApplicationExtension -> extension.privacySandbox.enable
                is LibraryExtension -> extension.privacySandbox.enable
                is DynamicFeatureExtension -> extension.privacySandbox.enable
                is TestExtension -> extension.privacySandbox.enable
                else -> false
            }
        }
}
