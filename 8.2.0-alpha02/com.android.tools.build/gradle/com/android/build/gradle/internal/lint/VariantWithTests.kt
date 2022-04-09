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

package com.android.build.gradle.internal.lint

import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.TestFixturesCreationConfig
import com.android.build.gradle.internal.component.UnitTestCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig

/**
 * A variant with its (optional) test components for consumption by lint.
 *
 * Note that for lintVital the test components will be omitted, even if they exist, as they
 * should not be analyzed.
 */
class VariantWithTests(
    val main: VariantCreationConfig,
    val androidTest: AndroidTestCreationConfig? = null,
    val unitTest: UnitTestCreationConfig? = null,
    val testFixtures: TestFixturesCreationConfig? = null
)
