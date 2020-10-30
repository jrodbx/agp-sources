/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.Nullable;
import java.util.List;
import java.util.Set;

/**
 * Base class for NDK config file.
 */
public interface CoreNdkOptions {

    /**
     * The module name
     */
    @Nullable
    String getModuleName();

    /**
     * The C Flags
     */
    @Nullable
    String getcFlags();

    /**
     * The LD Libs
     */
    @Nullable
    List<String> getLdLibs();

    /**
     * Specifies the Application Binary Interfaces (ABI) that Gradle should build outputs for and
     * package with your APK.
     *
     * <p>You can list any subset of the <a
     * href="https://developer.android.com/ndk/guides/abis.html#sa">ABIs the NDK supports</a>, as
     * shown below:
     *
     * <pre>
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
     * </pre>
     *
     * <p>When this flag is not configured, Gradle builds and packages all available ABIs.
     *
     * <p>To reduce the size of your APK, consider <a
     * href="https://developer.android.com/studio/build/configure-apk-splits.html#configure-abi-split">
     * configuring multiple APKs based on ABI</a>—instead of creating one large APK with all
     * versions of your native libraries, Gradle creates a separate APK for each ABI you want to
     * support and only packages the files each ABI needs.
     */
    @Nullable
    Set<String> getAbiFilters();

    /**
     * The APP_STL value
     */
    @Nullable
    String getStl();

    /**
     * Number of parallel threads to spawn.
     */
    @Nullable
    Integer getJobs();
}
