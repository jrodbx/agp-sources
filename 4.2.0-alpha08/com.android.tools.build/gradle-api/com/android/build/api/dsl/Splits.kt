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
 * DSL object for configuring APK Splits options. Configuring this object allows you to build
 * [Multiple APKs](https://developer.android.com/studio/build/configure-apk-splits.html) and
 * [Configuration APKs](https://developer.android.com/topic/instant-apps/guides/config-splits.html).
 *
 * If your app targets multiple device configurations, such as different screen densities and
 * [Application Binary Interfaces (ABIs)](https://developer.android.com/ndk/guides/abis.html), you
 * might want to avoid packaging resources for all configurations into a single large APK. To reduce
 * download sizes for your users, the Android plugin and Google Play Store provide the following
 * strategies to generate and serve build artifacts that each target a different device
 * configuration--so users download only the resources they need:
 *
 * - [Multiple APKs](https://developer.android.com/studio/build/configure-apk-splits.html): use this
 * to generate multiple stand-alone APKs. Each APK contains the code and resources required for a
 * given device configuration. The Android plugin and Google Play Store support generating multiple
 * APKs based on screen density and ABI. Because each APK represents a standalone APK that you
 * upload to the Google Play Store, make sure you appropriately
 * [assign version codes to each APK](https://developer.android.com/studio/build/configure-apk-splits.html#configure-APK-versions)
 * so that you are able to manage updates later.
 *
 * - [Configuration APKs](https://developer.android.com/topic/instant-apps/guides/config-splits.html):
 * use this only if you're building [Android Instant Apps](https://developer.android.com/topic/instant-apps/index.html).
 * The Android plugin packages your app's device-agnostic code and resources in a base APK, and each
 * set of device-dependent binaries and resources in separate APKs, called configuration APKs.
 * Configuration APKs do not represent stand-alone versions of your app. That is, devices need to
 * download both the base APK and additional configuration APKs from the Google Play Store to run
 * your instant app. The Android plugin and Google Play Store support generating configuration APKs
 * based on screen density, ABI, and language locales. You specify properties in this block just as
 * you would when building multiple APKs. However, you need to also set
 * [`generatePureSplits`](https://google.github.io/android-gradle-dsl/current/com.android.build.gradle.BaseExtension.html#com.android.build.gradle.BaseExtension:generatePureSplits)
 * to `true`.
 */
@Incubating
interface Splits{

    /**
     * Encapsulates settings for
     * [building per-ABI APKs](https://developer.android.com/studio/build/configure-apk-splits.html#configure-abi-split).
     */
    val abi: AbiSplit

    /**
     * Encapsulates settings for <a
     * [building per-ABI APKs](https://developer.android.com/studio/build/configure-apk-splits.html#configure-abi-split).
     *
     * For more information about the properties you can configure in this block, see [AbiSplit].
     */
    fun abi(action: AbiSplit.() -> Unit)

    /**
     * Encapsulates settings for
     * [building per-density APKs](https://developer.android.com/studio/build/configure-apk-splits.html#configure-density-split).
     */
    val density: DensitySplit

    /**
     * Encapsulates settings for
     * [building per-density APKs](https://developer.android.com/studio/build/configure-apk-splits.html#configure-density-split).
     *
     * For more information about the properties you can configure in this block, see
     * [DensitySplit].
     */
    fun density(action: DensitySplit.() -> Unit)

    /**
     * Returns the list of ABIs that the plugin will generate separate APKs for.
     *
     * If this property returns `null`, it means the plugin will not generate separate per-ABI APKs.
     * That is, each APK will include binaries for all ABIs your project supports.
     *
     * @return a set of ABIs.
     */
    val abiFilters: Collection<String>

    /**
     * Returns the list of screen density configurations that the plugin will generate separate APKs
     * for.
     *
     * If this property returns `null`, it means the plugin will not generate separate per-density
     * APKs. That is, each APK will include resources for all screen density configurations your
     * project supports.
     *
     * @return a set of screen density configurations.
     */
    val densityFilters: Collection<String>
}