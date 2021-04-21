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

/**
 * Shared properties between DSL objects [ProductFlavor] and [DefaultConfig]
 */
@Incubating
interface BaseFlavor : VariantDimension {
    // TODO(b/140406102)
    /** The name of the flavor. */
    fun getName(): String

    /**
     * Test application ID.
     *
     * See [Set the Application ID](https://developer.android.com/studio/build/application-id.html)
     */
    var testApplicationId: String?

    /**
     * The minimum SDK version.
     * Setting this it will override previous calls of [minSdk] and [minSdkPreview] setters. Only
     * one of [minSdk] and [minSdkPreview] should be set.
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    var minSdk: Int?

    /**
     * The minimum SDK version.
     * Setting this it will override previous calls of [minSdk] and [minSdkPreview] setters. Only
     * one of [minSdk] and [minSdkPreview] should be set.
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    var minSdkPreview: String?

    /**
     * The renderscript target api, or null if not specified. This is only the value set on this
     * product flavor.
     */
    var renderscriptTargetApi: Int?

    /**
     * Whether the renderscript code should be compiled in support mode to make it compatible with
     * older versions of Android.
     *
     * True if support mode is enabled, false if not, and null if not specified.
     */
    var renderscriptSupportModeEnabled: Boolean?

    /**
     * Whether the renderscript BLAS support lib should be used to make it compatible with older
     * versions of Android.
     *
     * True if BLAS support lib is enabled, false if not, and null if not specified.
     */
    var renderscriptSupportModeBlasEnabled: Boolean?

    /**
     * Whether the renderscript code should be compiled to generate C/C++ bindings.
     * True for C/C++ generation, false for Java, null if not specified.
     */
    var renderscriptNdkModeEnabled: Boolean?

    /**
     * Test instrumentation runner class name.
     *
     * This is a fully qualified class name of the runner, e.g.
     * `android.test.InstrumentationTestRunner`
     *
     * See [instrumentation](http://developer.android.com/guide/topics/manifest/instrumentation-element.html).
     */
    var testInstrumentationRunner: String?

    /**
     * Test instrumentation runner custom arguments.
     *
     * e.g. `[key: "value"]` will give `adb shell am instrument -w -e key value com.example`...
     *
     * See [instrumentation](http://developer.android.com/guide/topics/manifest/instrumentation-element.html).
     *
     * Test runner arguments can also be specified from the command line:
     *
     * ```
     * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.size=medium
     * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.foo=bar
     * ```
     */
    val testInstrumentationRunnerArguments: MutableMap<String, String>

    /**
     * See [instrumentation](http://developer.android.com/guide/topics/manifest/instrumentation-element.html).
     */
    var testHandleProfiling: Boolean?

    /**
     * See [instrumentation](http://developer.android.com/guide/topics/manifest/instrumentation-element.html).
     */
    var testFunctionalTest: Boolean?

    /**
     * Specifies a list of
     * [alternative resources](https://d.android.com/guide/topics/resources/providing-resources.html#AlternativeResources)
     * to keep.
     *
     * For example, if you are using a library that includes language resources (such as
     * AppCompat or Google Play Services), then your APK includes all translated language strings
     * for the messages in those libraries whether the rest of your app is translated to the same
     * languages or not. If you'd like to keep only the languages that your app officially supports,
     * you can specify those languages using the `resConfigs` property, as shown in the
     * sample below. Any resources for languages not specified are removed.
     *
     * ````
     * android {
     *     defaultConfig {
     *         ...
     *         // Keeps language resources for only the locales specified below.
     *         resConfigs "en", "fr"
     *     }
     * }
     * ````
     *
     * You can also use this property to filter resources for screen densities. For example,
     * specifying `hdpi` removes all other screen density resources (such as `mdpi`,
     * `xhdpi`, etc) from the final APK.
     *
     * **Note:** `auto` is no longer supported because it created a number of
     * issues with multi-module projects. Instead, you should specify a list of locales that your
     * app supports, as shown in the sample above. Android plugin 3.1.0 and higher ignore the `
     * auto` argument, and Gradle packages all string resources your app and its dependencies
     * provide.
     *
     * To learn more, see
     * [Remove unused alternative resources](https://d.android.com/studio/build/shrink-code.html#unused-alt-resources).
     */
    val resourceConfigurations: MutableSet<String>

    /** Options to configure the build-time support for `vector` drawables. */
    val vectorDrawables: VectorDrawables

    /** Configures [VectorDrawables]. */
    fun vectorDrawables(action: VectorDrawables.() -> Unit)

    /**
     * Whether to enable unbundling mode for embedded wear app.
     *
     * If true, this enables the app to transition from an embedded wear app to one
     * distributed by the play store directly.
     */
    var wearAppUnbundled: Boolean?
}
