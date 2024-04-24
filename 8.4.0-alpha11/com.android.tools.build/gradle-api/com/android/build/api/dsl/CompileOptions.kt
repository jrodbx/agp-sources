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

import org.gradle.api.JavaVersion

/**
 * Java compilation options.
 */
interface CompileOptions {
    /**
     * Language level of the java source code.
     *
     * Similar to what [Gradle Java plugin](http://www.gradle.org/docs/current/userguide/java_plugin.html)
     * uses. Formats supported are:
     *
     * - `"1.6"`
     * - `1.6`
     * - `JavaVersion.Version_1_6`
     * - `"Version_1_6"`
     */
    var sourceCompatibility: JavaVersion

    /**
     * Language level of the java source code.
     *
     * Similar to what [Gradle Java plugin](http://www.gradle.org/docs/current/userguide/java_plugin.html)
     * uses. Formats supported are:
     *
     * - `"1.6"`
     * - `1.6`
     * - `JavaVersion.Version_1_6`
     * - `"Version_1_6"`
     */
    fun sourceCompatibility(sourceCompatibility: Any)

    /**
     * Version of the generated Java bytecode.
     *
     * Similar to what [Gradle Java plugin](http://www.gradle.org/docs/current/userguide/java_plugin.html)
     * uses. Formats supported are:
     *
     * - `"1.6"`
     * - `1.6`
     * - `JavaVersion.Version_1_6`
     * - `"Version_1_6"`
     */
    var targetCompatibility: JavaVersion

    /**
     * Version of the generated Java bytecode.
     *
     * Similar to what [Gradle Java plugin](http://www.gradle.org/docs/current/userguide/java_plugin.html)
     * uses. Formats supported are:
     *
     * - `"1.6"`
     * - `1.6`
     * - `JavaVersion.Version_1_6`
     * - `"Version_1_6"`
     */
    fun targetCompatibility(targetCompatibility: Any)

    /** Java source files encoding. */
    var encoding: String

    /** Whether core library desugaring is enabled. */
    var isCoreLibraryDesugaringEnabled: Boolean
}
