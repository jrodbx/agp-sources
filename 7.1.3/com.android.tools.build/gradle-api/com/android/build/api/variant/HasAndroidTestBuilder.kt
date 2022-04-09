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

/**
 * Interface that mark the potential existence of android tests associated with a variant.
 */
interface HasAndroidTestBuilder {

    /**
     * Set to `true` if the variant's has any android tests, false otherwise.
     * Value is [Boolean#True] by default.
     */
    @Deprecated("replaced with enableAndroidTest", ReplaceWith("enableAndroidTest"))
    var androidTestEnabled: Boolean

    /**
     * Set to `true` if the variant's has any android tests, false otherwise.
     * Value is [Boolean#True] by default.
     */
    var enableAndroidTest: Boolean
}
