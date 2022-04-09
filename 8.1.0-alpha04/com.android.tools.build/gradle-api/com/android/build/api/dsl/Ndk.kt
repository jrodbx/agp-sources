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

/**
 * DSL object for per-variant NDK settings, such as the ABI filter.
 */
interface Ndk {
    /** The module name */
    var moduleName: String?

    /** The C Flags */
    var cFlags: String?

    /** The LD Libs */
    val ldLibs: MutableList<String>?

    /**
     * Specifies the Application Binary Interfaces (ABI) that Gradle should build outputs for and
     * package with your APK.
     *
     * You can list any subset of the
     * [ABIs the NDK supports](https://developer.android.com/ndk/guides/abis.html#sa),
     * as shown below:
     *
     * ```
     * android {
     *     // Similar to other properties in the defaultConfig block, you can override
     *     // these properties for each product flavor in your build configuration.
     *     defaultConfig {
     *         ndk {
     *             // Tells Gradle to build outputs for the following ABIs and package
     *             // them into your APK.
     *             abiFilters 'x86', 'x86_64', 'armeabi'
     *         }
     *     }
     * }
     * ```
     *
     * When this flag is not configured, Gradle builds and packages all available ABIs.
     *
     * To reduce the size of your APK, consider
     * [configuring multiple APKs based on ABI](https://developer.android.com/studio/build/configure-apk-splits.html#configure-abi-split)—instead of creating one large APK with all
     * versions of your native libraries, Gradle creates a separate APK for each ABI you want to
     * support and only packages the files each ABI needs.
     */
    val abiFilters: MutableSet<String>

    /**
     * The APP_STL value
     */
    var stl: String?

    /**
     * Number of parallel threads to spawn.
     */
    var jobs: Int?

    /**
     * The type of debug metadata which will be packaged in the app bundle.
     *
     * <p>Supported values are 'none' (default, no native debug metadata will be packaged),
     * 'symbol_table' (only the symbol tables will be packaged), and 'full' (the debug info and
     * symbol tables will be packaged).
     *
     * <p>Example usage:
     *
     * <pre>
     * android {
     *     buildTypes {
     *         release {
     *             ndk {
     *                 debugSymbolLevel 'symbol_table'
     *             }
     *         }
     *     }
     * }
     * </pre>
     */
    var debugSymbolLevel: String?
}
