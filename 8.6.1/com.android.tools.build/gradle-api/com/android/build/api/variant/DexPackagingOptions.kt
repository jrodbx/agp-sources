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

package com.android.build.api.variant

import org.gradle.api.provider.Property

/**
 * Build-time properties for packaging dex files inside an APK [Component].
 *
 * This is accessed via [ApkPackaging.dex]
 */
interface DexPackagingOptions {

    /**
     * Whether to use the legacy convention of compressing all dex files in the APK.
     *
     * This property is initialized from the
     * [com.android.build.api.dsl.DexPackaging.useLegacyPackaging] DSL element, if set. If this
     * property and the DSL are unset, dex files will be uncompressed when minSdk >= 28. If this
     * property is set, the value will be fed directly to the corresponding Gradle tasks without
     * extra logic.
     *
     * This property does not affect dex file compression in APKs produced from app bundles.
     */
    val useLegacyPackaging: Property<Boolean>
}
