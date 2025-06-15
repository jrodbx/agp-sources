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

package com.android.build.api.variant.impl

import com.android.build.api.variant.AndroidResources
import com.android.build.api.variant.ApplicationAndroidResources
import com.android.build.gradle.internal.scope.BuildFeatureValues
import org.gradle.api.provider.SetProperty

class ApplicationAndroidResourcesImpl(
    genericAndroidResourcesImpl: AndroidResources,
    buildFeatures: BuildFeatureValues,
    override val generateLocaleConfig: Boolean,
    override val localeFilters: SetProperty<String>
): ApplicationAndroidResources, AndroidResourcesImpl(
    genericAndroidResourcesImpl.ignoreAssetsPatterns,
    genericAndroidResourcesImpl.aaptAdditionalParameters,
    genericAndroidResourcesImpl.noCompress,
    viewBinding = buildFeatures.viewBinding,
    dataBinding = buildFeatures.dataBinding
)
