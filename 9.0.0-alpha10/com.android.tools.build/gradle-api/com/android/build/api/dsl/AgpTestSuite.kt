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
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.base.TestSuite

/**
 * A test suite that runs with the Android Gradle Plugin.
 *
 * An [AgpTestSuite] can run against a single or multiple variants. Users should
 * use [com.android.build.api.dsl.AgpTestSuite.targetVariants] to identify the final list of
 * variants that will be tested.
 *
 * Although this is not strictly necessary, if the test suite has source code, it will be recompiled
 * for each variant it targets. This is to ensure the compatibility of the test suite with each
 * variant individually.
 *
 * Defines how different types of source files are organized and declared for a test suite
 * within the Android Gradle Plugin. Test suites can incorporate various kinds of sources,
 * each serving a distinct purpose and following specific conventions.
 *
 * All sources for a given test suite (e.g., one named `myTestSuite`) are typically organized
 * under the `src/myTestSuite/` directory, relative to the module's root. Within this
 * directory, specific source types will reside in conventional subdirectories.
 *
 * Three primary types of sources can be associated with a test suite using the methods
 * on this interface:
 *
 * 1.  **Asset Test Sources (configured via `assets`)**:
 *     -   **Content**: A single folder containing static files like XML, JSON, or other
 *         declarative resources that describe or support tests. These files are not compiled.
 *     -   **Purpose**: Typically interpreted directly by test execution engines (e.g., JUnit).
 *     -   **Default Location**: `src/<testSuiteName>/assets/`
 *
 * 2.  **Host Test Sources (configured via `hostJar`)**:
 *     -   **Content**: A single folder containing compilable source code (e.g., Kotlin or Java)
 *         intended for tests that run on the host machine (JVM).
 *     -   **Purpose**: Compiled before test execution. The compiled classes are then used by
 *         test engines.
 *     -   **Default Location**: `src/<testSuiteName>/` (which would then contain standard
 *         source layouts like `java/`, `kotlin/`, `resources/`)
 *
 * 3.  **Device Test Sources (configured via `testApk`)**:
 *     -   **Content**: A standard Android source set structure, including compilable
 *         source code (Kotlin/Java), Android resources (`res/`), Android assets (`assets/`),
 *         and an AndroidManifest.xml file.
 *     -   **Purpose**: Compiled into a test APK that runs on an Android device or emulator.
 *     -   **Default Location**: `src/<testSuiteName>/androidTest/` (which would then contain
 *         standard Android source layouts like `java/`, `res/`, `assets/`, etc.)
 *
 * **Configuration Notes:**
 * -   A test suite can include one or more of these source types. For example, a suite
 *     might define both `hostTest` sources and `assetTest` sources.
 * -   Currently, it is not possible to declare the same type of source (e.g., two `hostTest`
 *     blocks) for a single test suite.
 * -   Future versions of this API may introduce more flexibility, such as allowing multiple
 *     instances of the same source type or providing ways to customize the default
 *     directory names.
 *
 * Test execution engines can retrieve the locations or outputs of these configured sources
 * through properties defined in `com.android.build.api.variant.AgpTestSuiteInputParameters`
 * (e.g., `STATIC_FILES`, `TEST_CLASSES`, `TESTED_APKS`). *
 */
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
     * Defines which variant this tests suite targets.
     */
    @get:Incubating
    val targetVariants: MutableList<String>

    /**
     * Adds a asset folder to this test suite sources, containing static sources like xml, or json files.
     *
     * The folder will be named 'assets' by default and will be therefore located at
     * `src/<testSuiteName>/assets/`
     *
     * No compilation of the sources will be performed and the sources files will be provided to the
     * configured junit engines using the [AgpTestSuiteInputParameters.STATIC_FILES]
     */
    @Incubating
    fun assets(action: TestSuiteAssetsSpec.() -> Unit)

    /**
     * Adds a host test folder to this test suite sources, containing kotlin sources that will be
     * compiled.
     *
     * The folder will be named 'test' by default. It's the root path of the sources and may
     * contain subfolders like 'java', 'kotlin' and 'resources'. The full path will be by default
     * `src/<testSuiteName>/test/
     *
     * questionable :
     * Configured test engines can retrieve the compiled classes using the
     * [AgpTestSuiteInputParameters.TEST_CLASSES] property.
     */
    @Incubating
    fun hostJar(action: TestSuiteHostJarSpec.() -> Unit)

    /**
     * Adds a device test source folder to this test suite, containing all necessary sources to
     * create an APK.
     *
     * The parent folder will be 'androidTest' by default.
     *
     * The sources will be compiled to produce a test APK that can be retrieved by the test engine
     * using the [AgpTestSuiteInputParameters.TESTED_APKS] property.
     */
    @Incubating
    fun testApk(action: TestSuiteTestApkSpec.() -> Unit)

    /**
     * Targets for this test suite, must be manually created in order to provide the variants this
     * test suite applies to.
     */
    @Incubating
    override fun getTargets(): NamedDomainObjectContainer<AgpTestSuiteTarget>

    /**
     * Configure the test tasks for this test target.
     *
     * There can be one to many instances of [Test] tasks for a particular test suite target. For
     * instance, if the test suite targets more than one device, AGP may decide to create one [Test]
     * instance per device.
     *
     * The configuration block can use the [action]'s context parameter to disambiguate between each
     * [Test] task instance.
     *
     * Do not make assumption about how AGP decides to allocate [Test] task instances per device, as
     * each AGP version can potentially change it in future release, always use the context object to
     * determine what the [Test] task applies to.
     *
     * @param action a block to configure the [Test] tasks associated with this test suite target.
     */
    @Incubating
    fun configureTestTasks(action: Test.(context: TestTaskContext) -> Unit)
}
