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

package com.android.build.gradle.internal.testsuites.impl

import com.android.build.api.variant.TestSuiteTargetBuilder
import com.android.build.api.variant.impl.capitalizeFirstChar
import com.android.build.gradle.internal.dsl.AgpTestSuiteTargetImpl

class TestSuiteTargetBuilderImpl(
    private val dslDefinedTestSuiteTarget: AgpTestSuiteTargetImpl
): TestSuiteTargetBuilder {

    override var enable = true

    override val targetDevices = mutableListOf<String>().also {
        it.addAll(dslDefinedTestSuiteTarget.targetDevices)
    }

    override fun getName(): String {
        return dslDefinedTestSuiteTarget.name
    }

    /**
     * Internal Methods
     */

    /**
     * Define a unique name for this test suite target within the variant. This is used to generate
     * test task names and other unique identifiers.
     */
    internal fun uniqueName() =
        // so far, I am joining the targetDevices to the name in case we start creating more than
        // one test task instance per target (to target a different devices for instance).
        name + targetDevices.joinToString(separator = "_").capitalizeFirstChar()
}
