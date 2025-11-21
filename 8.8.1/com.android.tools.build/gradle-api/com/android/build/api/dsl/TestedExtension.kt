/*
 * Copyright (C) 2019 The Android Open Source Project
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

/**
 * Common extension properties for the Android Application. Library and Dynamic Feature Plugins that
 * relate to testing
 *
 *
 * Only the Android Gradle Plugin should create instances of this interface.
 */
interface TestedExtension {
    /**
     * Specifies the
     * [build type](https://developer.android.com/studio/build/build-variants.html#build-types)
     * that the plugin should use to test the module.
     *
     * By default, the Android plugin uses the "debug" build type. This means that when you
     * deploy your instrumented tests using `gradlew connectedAndroidTest`, it uses the
     * code and resources from the module's "debug" build type to create the test APK. The plugin
     * then deploys the "debug" version of both the module's APK and the test APK to a connected
     * device, and runs your tests.
     *
     * To change the test build type to something other than "debug", specify it as follows:
     *
     * ```
     * android {
     *     // Changes the test build type for instrumented tests to "stage".
     *     testBuildType "stage"
     * }
     * ```
     *
     * If your module configures
     * [product flavors](https://developer.android.com/studio/build/build-variants.html#product-flavors)
     * the plugin creates a test APK and deploys tests for each build variant that uses
     * the test build type. For example, consider if your module configures "debug" and "release"
     * build types, and "free" and "paid" product flavors. By default, when you run your
     * instrumented tests using `gradlew connectedAndroidTest`, the plugin performs
     * executes the following tasks:
     *
     * * `connectedFreeDebugAndroidTest`: builds and deploys a `freeDebug`
     *       test APK and module APK, and runs instrumented tests for that variant.
     * * `connectedPaidDebugAndroidTest`: builds and deploys a `paidDebug`
     *       test APK and module APK, and runs instrumented tests for that variant.
     *
     * To learn more, read
     * [Create instrumented test for a build variant](https://developer.android.com/studio/test/index.html#create_instrumented_test_for_a_build_variant)
     *
     * **Note:** You can execute `connected<BuildVariant>AndroidTest` tasks
     * only for build variants that use the test build type. So, by default, running
     * `connectedStageAndroidTest` results in the following build error:
     *
     * ```
     * Task 'connectedStageAndroidTest' not found in root project
     * ```
     *
     * You can resolve this issue by changing the test build type to "stage".
     */
    var testBuildType: String

    /**
     * The namespace used by the android test and unit test components for the generated R and
     * BuildConfig classes.
     */
    var testNamespace: String?

    /**
     * Options to configure the test fixtures.
     */
    @get:Incubating
    val testFixtures: TestFixtures

    /**
     * Options to configure the test fixtures.
     */
    @Incubating
    fun testFixtures(action: TestFixtures.() -> Unit)
}
