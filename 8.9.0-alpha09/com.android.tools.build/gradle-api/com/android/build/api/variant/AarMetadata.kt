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

import org.gradle.api.Incubating
import org.gradle.api.provider.Property

/**
 * Build-time properties for AAR Metadata inside a [Component]
 *
 * This is accessed via [GeneratesAar.aarMetadata]
 */
interface AarMetadata {

    /**
     * Minimum compileSdkVersion needed to consume this library. This is the minimum sdk version a
     * module must use in order to import this library.
     */
    val minCompileSdk: Property<Int>

    /**
     * Minimum compileSdkExtension needed to consume this library. This is the minimum sdk extension
     * version a module must use in order to import this library.
     *
     * The default value of [minCompileSdkExtension] is 0 if not set via the DSL.
     */
    @get:Incubating
    val minCompileSdkExtension: Property<Int>

    /**
     * Minimum Android Gradle Plugin version needed to consume this library. This is the minimum AGP
     * version a module must use in order to import this library.
     *
     * minAgpVersion must be a stable AGP version, and it must be formatted with major, minor, and
     * micro values (for example, "4.0.0").
     */
    @get:Incubating
    val minAgpVersion: Property<String>
}
