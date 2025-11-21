/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.variant;

/** Whether to use splits or multi-apk for this variant. */
public enum MultiOutputPolicy {
    /**
     * For releases before Lollipop (API 21), to reduce APK size, developers can configure their app
     * build to create multiple APKs
     *
     * <p>Each APK is the entire application, but specialized to one device configuration along
     * split dimensions, which are ABI, density and language.
     *
     * <p>The dimensions are multiplicative. For example if the app is distributed for three ABIs,
     * with five densities and 6 different languages, that will result in 3*5*6 = 90 APKs.
     *
     * <p>In some places this is still call 'full splits', but we now prefer 'multi apk'
     */
    MULTI_APK,

    /**
     * Android Lollipop (API 21) supports split APKs, where the app is composed of a base APK
     * containing common code and resources, and multiple split APKs containing just the resources
     * or libraries for that dimension.
     *
     * <p>The dimensions are additive. For example if the app is distributed for three ABIs, with
     * five densities and 6 different languages, that will result in one base APK and 3+5+6 = 15
     * split APKs.
     */
    SPLITS
}
