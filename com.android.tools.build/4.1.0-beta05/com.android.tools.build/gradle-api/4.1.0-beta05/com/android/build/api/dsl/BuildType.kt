/*
 * Copyright (C) 2019 The Android Open Source Project
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
import org.gradle.api.Named

/** DSL object to configure build types. */
@Incubating
interface BuildType : Named,
    VariantDimension {
    /**
     * Whether test coverage is enabled for this build type.
     *
     * If enabled this uses Jacoco to capture coverage and creates a report in the build
     * directory.
     *
     * The version of Jacoco can be configured with:
     * ```
     * android {
     *     jacoco {
     *         version = '0.6.2.201302030002'
     *     }
     * }
     * ```
     */
    var isTestCoverageEnabled: Boolean

    /**
     * Specifies whether the plugin should generate resources for pseudolocales.
     *
     * A pseudolocale is a locale that simulates characteristics of languages that cause UI,
     * layout, and other translation-related problems when an app is localized. Pseudolocales can
     * aid your development workflow because you can test and make adjustments to your UI before you
     * finalize text for translation.
     *
     * When you set this property to `true` as shown below, the plugin generates
     * resources for the following pseudo locales and makes them available in your connected
     * device's language preferences: `en-XA` and `ar-XB`.
     *
     * ```
     * android {
     *     buildTypes {
     *         debug {
     *             pseudoLocalesEnabled true
     *         }
     *     }
     * }
     * ```
     *
     * When you build your app, the plugin includes the pseudolocale resources in your APK. If
     * you notice that your APK does not include those locale resources, make sure your build
     * configuration isn't limiting which locale resources are packaged with your APK, such as using
     * the `resConfigs` property to
     * [remove unused locale resources](https://d.android.com/studio/build/shrink-code.html#unused-alt-resources).
     *
     * To learn more, read
     * [Test Your App with Pseudolocales](https://d.android.com/guide/topics/resources/pseudolocales.html).
     */
    var isPseudoLocalesEnabled: Boolean

    /**
     * Whether this build type is configured to generate an APK with debuggable native code.
     */
    var isJniDebuggable: Boolean

    /**
     * Whether the build type is configured to generate an apk with debuggable RenderScript code.
     */
    var isRenderscriptDebuggable: Boolean

    /** Optimization level to use by the renderscript compiler.  */
    var renderscriptOptimLevel: Int

    /**
     * Specifies whether to enable code shrinking for this build type.
     *
     * By default, when you enable code shrinking by setting this property to `true`,
     * the Android plugin uses ProGuard.
     *
     * To learn more, read
     * [Shrink Your Code and Resources](https://developer.android.com/studio/build/shrink-code.html).
     */
    var isMinifyEnabled: Boolean

    /**
     * Specifies a sorted list of build types that the plugin should try to use when a direct
     * variant match with a local module dependency is not possible.
     *
     *
     * Android plugin 3.0.0 and higher try to match each variant of your module with the same one
     * from its dependencies. For example, when you build a "freeDebug" version of your app, the
     * plugin tries to match it with "freeDebug" versions of the local library modules the app
     * depends on.
     *
     *
     * However, there may be situations in which **your app includes build types that a
     * dependency does not**. For example, consider if your app includes a "stage" build type, but
     * a dependency includes only a "debug" and "release" build type. When the plugin tries to build
     * the "stage" version of your app, it won't know which version of the dependency to use, and
     * you'll see an error message similar to the following:
     *
     * ```
     * Error:Failed to resolve: Could not resolve project :mylibrary.
     * Required by:
     * project :app
     * ```
     *
     *
     * In this situation, you can use `matchingFallbacks` to specify alternative
     * matches for the app's "stage" build type, as shown below:
     *
     * ```
     * // In the app's build.gradle file.
     * android {
     *     buildTypes {
     *         release {
     *             // Because the dependency already includes a "release" build type,
     *             // you don't need to provide a list of fallbacks here.
     *         }
     *         stage {
     *             // Specifies a sorted list of fallback build types that the
     *             // plugin should try to use when a dependency does not include a
     *             // "stage" build type. You may specify as many fallbacks as you
     *             // like, and the plugin selects the first build type that's
     *             // available in the dependency.
     *             matchingFallbacks = ['debug', 'qa', 'release']
     *         }
     *     }
     * }
     * ```
     *
     *
     * Note that there is no issue when a library dependency includes a build type that your app
     * does not. That's because the plugin simply never requests that build type from the
     * dependency.
     *
     * @return the names of product flavors to use, in descending priority order
     */
    val matchingFallbacks: MutableList<String>
}
