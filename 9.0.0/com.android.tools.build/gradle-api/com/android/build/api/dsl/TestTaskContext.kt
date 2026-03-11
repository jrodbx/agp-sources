/*
 * Copyright (C) 2025 The Android Open Source Project
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

@Incubating
interface TestTaskContext {

    /**
     * [AgpTestSuiteTarget] name the [org.gradle.api.tasks.testing.Test] task is targeting.
     */
    @get:Incubating
    val targetName: String

    /**
     * Suite name the [org.gradle.api.tasks.testing.Test] task is testing.
     *
     * TODO : Maybe replace with TestSuite Variant object once the variant interfaces become
     * public.
     */
    @get:Incubating
    val suiteName: String

    /**
     * Targeted variant the [org.gradle.api.tasks.testing.Test] task is running against.
     *
     * TODO: Replace with [com.android.build.api.variant.Component] ?
     */
    @get:Incubating
    val targetedVariant: String

    /**
     * Returns the list of devices this [org.gradle.api.tasks.testing.Test] task targets. In case the test runs on the host
     * machine, the list will be empty.
     */
    @get:Incubating
    val targetedDevices: Collection<String>
}
