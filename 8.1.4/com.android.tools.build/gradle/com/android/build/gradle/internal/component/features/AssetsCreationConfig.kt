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

package com.android.build.gradle.internal.component.features

import com.android.build.api.variant.AndroidResources

/**
 * Creation config for components that support assets.
 *
 * To use this in a task that requires assets support, use
 * [com.android.build.gradle.internal.tasks.factory.features.AssetsTaskCreationAction].
 * Otherwise, access the nullable property on the component
 * [com.android.build.gradle.internal.component.ComponentCreationConfig.assetsCreationConfig].
 */
interface AssetsCreationConfig {
    /**
     * AndroidResources block currently contains asset options, while disabling android resources
     * doesn't disable assets. To work around this, AndroidResources block is duplicated between
     * here and [AndroidResourcesCreationConfig]. If android resources is enabled, the value will
     * correspond to the same object as [AndroidResourcesCreationConfig.androidResources].
     */
    val androidResources: AndroidResources
}
