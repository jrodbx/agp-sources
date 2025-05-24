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

import org.gradle.api.provider.SetProperty

/**
 * Build-time properties for packaging native libraries (.so) inside a [Component].
 *
 * This is accessed via [Packaging.jniLibs]
 */
interface JniLibsPackaging {

    /**
     * The set of excluded patterns. Native libraries matching any of these patterns do not get
     * packaged.
     *
     * Example usage: `packaging.jniLibs.excludes.add("**`/`exclude.so")`
     */
    val excludes: SetProperty<String>

    /**
     * The set of patterns for which the first occurrence is packaged in the APK. For each native
     * library APK entry path matching one of these patterns, only the first native library found
     * with that path gets packaged.
     *
     * Example usage: `packaging.jniLibs.pickFirsts.add("**`/`pickFirst.so")`
     */
    val pickFirsts: SetProperty<String>

    /**
     * The set of patterns for native libraries that should not be stripped of debug symbols.
     *
     * Example: `packaging.jniLibs.keepDebugSymbols.add("**`/`doNotStrip.so")`
     */
    val keepDebugSymbols: SetProperty<String>
}
