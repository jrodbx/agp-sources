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
 * Optional settings for the Compose feature.
 */
interface ComposeOptions {
    /**
     * Sets the version of the Kotlin Compiler used to compile the project or null if using
     * the default one.
     */
    @Deprecated("Android Gradle Plugin will ignore this option and use the kotlin compiler version that is set in the build script.")
    var kotlinCompilerVersion: String?

    /**
     * Sets the version of the Kotlin Compiler extension for the project or null if using
     * the default one.
     */
    var kotlinCompilerExtensionVersion: String?

    /**
     * Enables live literals in Compose
     */
    var useLiveLiterals: Boolean
}
