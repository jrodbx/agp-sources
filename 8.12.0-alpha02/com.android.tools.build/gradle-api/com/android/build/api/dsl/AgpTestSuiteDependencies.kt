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
import org.gradle.api.artifacts.dsl.DependencyCollector

/**
 * DSL element to add dependencies to an [AgpTestSuite]
 */
/** @suppress */
@Incubating
interface AgpTestSuiteDependencies {

    /**
     * Returns a [DependencyCollector] that collects the set of compile-only dependencies.
     */
    @get:Incubating
    val compileOnly: DependencyCollector

    /**
     * Returns a []DependencyCollector] that collects the set of implementation dependencies.
     */
    @get:Incubating
    val implementation: DependencyCollector

    /**
     * Returns a [DependencyCollector] that collects the set of runtime-only dependencies.
     */
    @get:Incubating
    val runtimeOnly: DependencyCollector
}
