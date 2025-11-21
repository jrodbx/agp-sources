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
 * Build-time properties for packaging native libraries (.so) inside an APK [Component].
 *
 * This is accessed via [ApkPackaging.jniLibs]
 */
interface JniLibsApkPackaging : JniLibsPackaging {

    /**
     * Whether to use the legacy convention of compressing all .so files in the APK. This does not
     * affect APKs generated from the app bundle; see [useLegacyPackagingFromBundle].
     *
     * This property is initialized from the
     * [com.android.build.api.dsl.JniLibsPackaging.useLegacyPackaging] DSL element, if set. If this
     * property and the DSL are unset, .so files will be uncompressed and page-aligned
     * when minSdk >= 23. If this property is set, the value will be fed directly to the
     * corresponding Gradle tasks without extra logic.
     *
     * If setting this property, consider also setting [useLegacyPackagingFromBundle] to the same
     * value so that .so files are packaged consistently when API level >= 23, whether an APK is
     * generated from the app bundle or not.
     */
    val useLegacyPackaging: Property<Boolean>

    /**
     * Whether to use the legacy convention of compressing all .so files when generating APKs from
     * the app bundle. If true, .so files will always be compressed when generating APKs from the
     * app bundle, regardless of the API level of the target device. If false, .so files will be
     * compressed only when targeting devices with API level < 23.
     *
     * This property is initialized from the
     * [com.android.build.api.dsl.JniLibsPackaging.useLegacyPackaging] DSL element, if set. If this
     * property and the DSL are unset, this property's value defaults to false.
     *
     * If setting this property to true, consider also setting [useLegacyPackaging] to true so that
     * .so files are packaged consistently, whether an APK is generated from the app bundle or not.
     */
    val useLegacyPackagingFromBundle: Property<Boolean>
}
