/*
 * Copyright (C) 2021 The Android Open Source Project
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

import org.gradle.api.provider.MapProperty

/**
 * Model for test components that contains build-time properties.
 *
 * These components are attached to a main component ([Variant]), and accessed via them.
 *
 * ```kotlin
 * androidComponents {
 *   onVariants(selector().all()) { variant: ApplicationVariant ->
 *     variant.androidTest?.apply {
 *       ....
 *     }
 *     variant.unitTest?.apply {
 *       ....
 *     }
 *   }
 * }
 * ```
 *
 * Not all subtype of [Variant] will have access to all sub-types of [TestComponent], this
 * is handled via [HasUnitTest], and [HasAndroidTest].
 *
 * The test components are also part of [Variant.nestedComponents]
 *
 */
interface TestComponent: Component {
    /**
     * [MapProperty] of the test component's manifest placeholders.
     *
     * Placeholders are organized with a key and a value. The value is a [String] that will be
     * used as is in the merged manifest.
     *
     * @return the [MapProperty] with keys as [String]
     */
    val manifestPlaceholders: MapProperty<String, String>
}
