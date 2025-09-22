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

package com.android.build.api.variant.impl

import com.android.build.gradle.internal.component.TestSuiteTargetCreationConfig
import com.android.build.gradle.internal.testsuites.TestSuiteTarget
import com.android.build.gradle.internal.testsuites.impl.TestSuiteTargetBuilderImpl

class TestSuiteTargetImpl(
    private val testSuiteBuilder: TestSuiteTargetBuilderImpl,
    override val testTaskName: String,
): TestSuiteTargetCreationConfig, TestSuiteTarget {

    override val enabled: Boolean = testSuiteBuilder.enable

    override fun getName(): String = testSuiteBuilder.name

    override val targetDevices: Collection<String> = testSuiteBuilder.targetDevices
}
