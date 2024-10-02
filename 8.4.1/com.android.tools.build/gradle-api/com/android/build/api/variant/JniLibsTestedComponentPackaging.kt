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
import org.gradle.api.provider.SetProperty

/**
 * Build-time properties for packaging native libraries (.so) inside a tested [Component].
 *
 * This is accessed via [TestedComponentPackaging.jniLibs]
 */
@Incubating
interface JniLibsTestedComponentPackaging : JniLibsPackaging {

    /**
     * The set of test-only patterns. Native libraries matching any of these patterns do not get
     * packaged in the main APK or AAR, but they are included in the test APK.
     *
     * Example: `packaging.jniLibs.testOnly.add("**`/`testOnly.so")`
     */
    @get:Incubating
    val testOnly: SetProperty<String>
}
