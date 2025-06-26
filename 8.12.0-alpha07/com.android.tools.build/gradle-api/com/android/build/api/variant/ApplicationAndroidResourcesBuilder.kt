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

package com.android.build.api.variant

import org.gradle.api.Incubating

/**
 * Build-time properties for Android Resources inside a [Component].
 * Specialization of [AndroidResourcesBuilder] for modules that applied the `com.android.application` plugin.
 *
 * This is accessed via [ApplicationVariantBuilder.androidResources]
 */
@Incubating
interface ApplicationAndroidResourcesBuilder: AndroidResourcesBuilder {
    /**
     * Property that automatically generates locale config when enabled.
     */
    @get:Incubating
    @set:Incubating
    var generateLocaleConfig: Boolean
}
