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

import com.android.build.gradle.internal.api.TestSuiteSourceSet
import com.android.build.gradle.internal.dependency.TestSuiteSourceClasspath

/**
 * Each test suite source type will be processed in isolation, most likely using a
 * [com.android.build.api.artifact.Artifacts] instance to store intermediate files, etc...
 * Each source type has its own set of dependencies which are independent from other sources on
 * the same test suite.
 *
 * The TestSuiteSourceContainer represent the isolated container for sources and their derivatives
 * (like compileClasspath) for a particular test suite.
 */
class TestSuiteSourceContainer(
    internal val name: String,
    internal val source: TestSuiteSourceSet,
    internal val dependencies: TestSuiteSourceClasspath,
) {
}
