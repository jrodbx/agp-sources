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

/**
 * Interface for component builder that specifies whether to include SDK dependency information
 * in APKs and Bundles for the variant
 *
 * Including dependency information in your APK or Bundle allows Google Play to ensure that
 * any third-party software your app uses complies with
 * [Google Play's Developer Program Policies](https://support.google.com/googleplay/android-developer/topic/9858052).
 * For more information, see the Play Console support page
 * [Using third-party SDKs in your app](https://support.google.com/googleplay/android-developer/answer/10358880).
 */
interface DependenciesInfoBuilder {
    @Deprecated("This property is renamed to includeInApk", replaceWith = ReplaceWith("includeInApk"))
    var includedInApk: Boolean
    @Deprecated("This property is renamed to includeInBundle", replaceWith = ReplaceWith("includeInBundle"))
    var includedInBundle: Boolean

    /**
     * Set to `true` if information about SDK dependencies of an APK should be added to its signature
     * block, `false` otherwise.
     *
     * Default value will match [com.android.build.api.dsl.DependenciesInfo.includeInApk]
     *
     * It's not safe to read this value. Use [DependenciesInfo.includedInApk] instead (found on
     * [ApplicationVariant]
     */
    var includeInApk: Boolean

    /**
     * Whether information about SDK dependencies of an App Bundle will be added to it.
     */

    /**
     * Set to `true` if information about SDK dependencies of an App Bundle should be added to it,
     * `false` otherwise.
     *
     * Default value will match [com.android.build.api.dsl.DependenciesInfo.includeInBundle]
     *
     * It's not safe to read this value. Use [DependenciesInfo.includedInBundle] instead (found on
     * [ApplicationVariant]
     */
    var includeInBundle: Boolean
}
