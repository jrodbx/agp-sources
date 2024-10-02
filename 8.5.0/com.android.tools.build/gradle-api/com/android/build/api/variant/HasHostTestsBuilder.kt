/*
 * Copyright (C) 2023 The Android Open Source Project
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
 * Interface that marks the potential existence of [HostTest] components on a [Variant].
 *
 * This is implemented by select subtypes of [VariantBuilder].
 */
@Incubating
interface HasHostTestsBuilder {

    /**
     * Variant's [HostTestBuilder] configuration to turn on or off screenshot tests and set
     * other screenshot test related settings.
     *
     * @return a map which keys are unique names within the tested variant like [UNIT_TEST_TYPE] or
     * [SCREENSHOT_TEST_TYPE] and the values are [HostTestBuilder] for that host test suite.
     */
    @get:Incubating
    val hostTests: Map<String, HostTestBuilder>
}
