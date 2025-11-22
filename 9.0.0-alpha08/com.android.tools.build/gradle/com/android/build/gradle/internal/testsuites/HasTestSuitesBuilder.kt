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

package com.android.build.gradle.internal.testsuites

import com.android.build.api.variant.TestSuiteBuilder
import org.gradle.api.Incubating

/**
 * Container of [com.android.build.api.variant.TestSuiteBuilder]
 */
@Incubating
interface HasTestSuitesBuilder {
    /**
     * Variant's [com.android.build.api.variant.TestSuiteBuilder] configuration to configure test suites associated with this
     * variant.
     *
     * @return a [Map] which keys are unique names within the test suites
     *
     */
    @get:Incubating
    val suites: Map<String, TestSuiteBuilder>
}
