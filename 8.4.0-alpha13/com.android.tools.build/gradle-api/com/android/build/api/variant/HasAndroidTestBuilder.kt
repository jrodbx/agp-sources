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

/**
 * Interface that marks the potential existence of [AndroidTest] component on a [Variant].
 *
 * This is implemented by select subtypes of [VariantBuilder].
 */
@Deprecated(message="replaced with HasDeviceTestsBuilder", ReplaceWith("HasDeviceTestsBuilder"))
interface HasAndroidTestBuilder {

    /**
     * Set to `true` if the variant's has any android tests, false otherwise.
     * Value is [Boolean#True] by default.
     */
    @Deprecated("replaced with DeviceTest.enable", ReplaceWith("(this as DeviceTest).enable"))
    var androidTestEnabled: Boolean

    /**
     * Set to `true` if the variant's has any android tests, false otherwise.
     * Value is [Boolean#True] by default.
     */
    @Deprecated("replaced with DeviceTest.enable", ReplaceWith("(this as DeviceTest).enable"))
    var enableAndroidTest: Boolean

    /**
     * Variant's [AndroidTestBuilder] configuration to turn on or off android tests and set
     * other android test related settings.
     */
    @Suppress("DEPRECATION")
    @get:Deprecated("replaced with DeviceTestBuilder.deviceTest")
    val androidTest: AndroidTestBuilder
}
