/*
 * Copyright (C) 2024 The Android Open Source Project
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
 * [Variant] that optionally have [HostTest] components like [UnitTest].
 */
@Incubating
interface HasHostTests {
    /**
     * [Map] of Variant's [HostTest], or empty if all host tests (like unit test) for this variant
     * are disabled. Map keys are the [HostTest] name, like [HasHostTestsBuilder.UNIT_TEST_TYPE] or
     * [HasHostTestsBuilder.SCREENSHOT_TEST_TYPE] for default host tests.
     */
    @get:Incubating
    val hostTests: Map<String, HostTest>
}
