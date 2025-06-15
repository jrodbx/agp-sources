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

package com.android.build.api.dsl

/**
 * Maven publishing DSL object for configuring options related to publishing APK and AAB.
 *
 * This following code example creates a publication for the fullRelease build variant, which
 * publish your app as Android App Bundle.
 *
 * ```
 * android {
 *     // This project has four build variants: fullDebug, fullRelease, demoDebug, demoRelease
 *     flavorDimensions 'mode'
 *     productFlavors {
 *         full {}
 *         demo {}
 *     }
 *
 *     publishing {
 *         // Publish your app as an AAB
 *         singleVariant("fullRelease")
 *     }
 * }
 *
 * afterEvaluate {
 *     publishing {
 *         publications {
 *             fullRelease(MavenPublication) {
 *                 from components.fullRelease
 *                 // ......
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * To publish your app as a ZIP file of APKs, simply use the [ApplicationSingleVariant.publishApk]
 * as shown in the following example.
 *
 * ```
 * android {
 *     publishing {
 *         // Publish your app as a ZIP file of APKs
 *         singleVariant("fullRelease") {
 *             publishApk()
 *         }
 *     }
 * }
 * ```
 */
interface ApplicationPublishing : Publishing<ApplicationSingleVariant>
