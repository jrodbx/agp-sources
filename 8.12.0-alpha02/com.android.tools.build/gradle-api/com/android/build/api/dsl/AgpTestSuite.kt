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

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.testing.base.TestSuite

/**
 * A test suite that runs with the Android Gradle Plugin.
 *
 * An [AgpTestSuite] can run against a single multiple product flavors or variants. Users should
 * use a combination of the [targetProductFlavors] and [targetVariants] to identify the final
 * list of variants that will be tested.
 *
 * Although this is not strictly necessary, if the test suite has source code, it will be recompiled
 * for each variant it targets. This is to ensure the compatibility of the test suite with each
 * variant individually.
 *
 * TODO : resolve : should we allow to target BuildTypes ?
 * A subsequent CL will introduce sub types for host vs devices tests and this comment
 *
 */
/** @suppress */
@Suppress("UnstableApiUsage")
@Incubating
interface AgpTestSuite: TestSuite {

    /**
     * Spec to identify the test engine that will be used to run this test suite. Do not call this
     * method if the test suite should use a dedicated test task. Calling this method will direct
     * AGP to create a [org.gradle.api.tasks.testing.Test] task and configure it using the
     * returned [JUnitEngineSpec]
     */
    @get:Incubating
    val useJunitEngine: JUnitEngineSpec

    /**
     * Specifies properties for the JUnit test engines to run in this test suite
     */
    @Incubating
    fun useJunitEngine(action: JUnitEngineSpec.() -> Unit)

    /**
     * Sets the list of [ProductFlavor]s this test suite will target.
     *
     * The list must be finalized during configuration time as we must create compilation and
     * test tasks to execute the suites.
     *
     * Each targeted product flavors is expressed as a pair with the product flavor dimension first
     * and the product flavor value second.
     *
     * [targetProductFlavors] and [targetVariants] are additive, which mean that a variant is selected
     * if one of its product flavors is in the [targetProductFlavors] list OR if the variant name is in
     * the [targetVariants] list.
     */
    @get:Incubating
    val targetProductFlavors: MutableList<Pair<String, String>>

    /**
     * Sets the list of Variants names this test suite will target
     *
     * The list must be finalized during configuration time as we must create compilation and
     * test tasks to execute the suites.
     *
     * [targetProductFlavors] and [targetVariants] are additive, which mean that a variant is selected
     * if one of its product flavors is in the [targetProductFlavors] list OR if the variant name is in
     * the [targetVariants] list.
     */
    @get:Incubating
    val targetVariants: MutableList<String>

    /**
     * Dependency handler for this test suite. For now, both the test sources dependencies as well
     * as the test engines dependencies must be configured through this object. However, in a
     * future version of this API, the test sources dependencies will move to the source set
     * definition.
     */
    @get:Incubating
    val dependencies: AgpTestSuiteDependencies

    /**
     * Specifies dependency information for this test suite.
     */
    @Incubating
    fun dependencies(action: AgpTestSuiteDependencies.() -> Unit)
}
