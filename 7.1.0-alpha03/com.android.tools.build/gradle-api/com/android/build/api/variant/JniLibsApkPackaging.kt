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

import org.gradle.api.provider.Provider

/**
 * Defines an APK variant's packaging options for native library (.so) files.
 */
interface JniLibsApkPackaging : JniLibsPackaging {

    /**
     * Whether to use the legacy convention of compressing all .so files in the APK. This does not
     * affect APKs generated from the app bundle; see [useLegacyPackagingFromBundle].
     */
    val useLegacyPackaging: Provider<Boolean>

    /**
     * Whether to use the legacy convention of compressing all .so files when generating APKs from
     * the app bundle. If true, .so files will always be compressed when generating APKs from the
     * app bundle, regardless of the API level of the target device. If false, .so files will be
     * compressed only when targeting devices with API level < M.
     */
    val useLegacyPackagingFromBundle: Provider<Boolean>
}
