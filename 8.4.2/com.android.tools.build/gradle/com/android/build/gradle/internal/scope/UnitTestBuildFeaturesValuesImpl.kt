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

package com.android.build.gradle.internal.scope

import com.android.build.api.dsl.BuildFeatures
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.ComponentType

class UnitTestBuildFeaturesValuesImpl(
    buildFeatures: BuildFeatures,
    projectOptions: ProjectOptions,
    dataBindingOverride: Boolean? = null,
    mlModelBindingOverride: Boolean? = null,
    includeAndroidResources: Boolean,
    testedComponent: ComponentType
) : BuildFeatureValuesImpl(
    buildFeatures,
    projectOptions,
    dataBindingOverride,
    mlModelBindingOverride
) {
    // We only create android resources tasks for unit test components when the tested component is
    // a library variant and the user specifies to includeAndroidResources. Otherwise, the tested
    // resources and assets are just copied as the unit test resources and assets output.
    override val androidResources: Boolean = includeAndroidResources && testedComponent.isAar
}
