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

package com.android.build.api.dsl

/**
 * Build types define certain properties that Gradle uses when building and packaging your library,
 * and are typically configured for different stages of your development lifecycle.
 *
 * Test projects have a target application project that they depend on and build type matching works
 * in the same way as library dependencies. Therefore to test multiple build types of an
 * application you can declare corresponding build types here.
 *
 * See
 * [configuring build types](https://developer.android.com/studio/build#build-config)
 * for more information.
 */
interface TestBuildType :
    BuildType,
    TestVariantDimension {
    /** Whether this build type should generate a debuggable apk. */
    var isDebuggable: Boolean

    /**
     * Whether to crunch PNGs.
     *
     * Setting this property to `true` reduces of PNG resources that are not already
     * optimally compressed. However, this process increases build times.
     *
     * PNG crunching is enabled by default in the release build type and disabled by default in
     * the debug build type.
     */
    var isCrunchPngs: Boolean?
}
