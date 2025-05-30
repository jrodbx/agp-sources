/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.build.api.dsl.PrivacySandboxKeepRules
import com.android.build.api.dsl.PrivacySandboxSdkOptimization
import com.android.build.gradle.internal.services.DslServices
import javax.inject.Inject

abstract class PrivacySandboxSdkOptimizationImpl: PrivacySandboxSdkOptimization

abstract class PrivacySandboxKeepRulesImpl @Inject constructor(
    dslService: DslServices
): PrivacySandboxKeepRules, ConfigurableFilesImpl(dslService)
