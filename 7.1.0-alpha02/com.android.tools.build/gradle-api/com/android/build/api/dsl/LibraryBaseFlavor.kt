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

import org.gradle.api.Incubating

interface LibraryBaseFlavor :
    BaseFlavor,
    LibraryVariantDimension {

    /**
     * The target SDK version used for building the test APK.
     *
     * This is propagated in the library manifest, but that is only advisory for libraries that
     * depend on this library.
     *
     * Setting this it will override previous calls of [targetSdk] and [targetSdkPreview] setters.
     * Only one of [targetSdk] and [targetSdkPreview] should be set.
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    @get:Incubating
    @set:Incubating
    var targetSdk: Int?

    @Deprecated("Replaced by targetSdk property")
    @Incubating
    fun targetSdkVersion(targetSdkVersion: Int)

    /**
     * The target SDK version used for building the test APK.
     *
     * This is propagated in the library manifest, but that is only advisory for libraries that
     * depend on this library.
     *
     * Setting this it will override previous calls of [targetSdk] and [targetSdkPreview] setters.
     * Only one of [targetSdk] and [targetSdkPreview] should be set.
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    @get:Incubating
    @set:Incubating
    var targetSdkPreview: String?

    @Deprecated("Replaced by targetSdkPreview property")
    @Incubating
    fun setTargetSdkVersion(targetSdkVersion: String?)

    @Deprecated("Replaced by targetSdkPreview property")
    @Incubating
    fun targetSdkVersion(targetSdkVersion: String?)
}
