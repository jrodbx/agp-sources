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

import org.gradle.api.artifacts.dsl.DependencyCollector

/**
 * Variant Specific test suite dependencies. The [DependencyCollector]s returned by this
 * interface will not contain the dependencies that were added during the test suite DSL definition.
 * Therefore, calling [DependencyCollector.getDependencies] on those will only return the dependencies
 * that were added here.
 *
 * These [DependencyCollector]s can only be used to add new dependencies that are specific to the
 * current variant. If you need to add a dependency for the test suite that will apply to all the
 * variants, use the [com.android.build.api.dsl.AgpTestSuite.dependencies].
 *
 * TODO: Should we no have this and just use the com.android.build.api.dsl.AgpTestSuiteDependencies version ?
 */
interface TestSuiteDependencies {

    /**
     * Returns a [DependencyCollector] that collects the set of compile-only dependencies.
     */
    val compileOnly: DependencyCollector

    /**
     * Returns a []DependencyCollector] that collects the set of implementation dependencies.
     */
    val implementation: DependencyCollector

    /**
     * Returns a [DependencyCollector] that collects the set of runtime-only dependencies.
     */
    val runtimeOnly: DependencyCollector
}
