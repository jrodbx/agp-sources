/*
 * Copyright (C) 2023 The Android Open Source Project
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

@Incubating
interface KotlinMultiplatformAndroidTestOnDevice {
    /**
     * The test application id.
     */
    @get:Incubating
    @set:Incubating
    var applicationId: String?

    /**
     * Test instrumentation runner class name.
     * This is a fully qualified class name of the runner
     * See [instrumentation](http://developer.android.com/guide/topics/manifest/instrumentation-element.html).
     */
    @get:Incubating
    @set:Incubating
    var instrumentationRunner: String?

    /**
     * Test instrumentation runner custom arguments.
     *
     * e.g. `[key: "value"]` will give `adb shell am instrument -w -e key value com.example`...
     *
     * See [instrumentation](http://developer.android.com/guide/topics/manifest/instrumentation-element.html).
     *
     * Test runner arguments can also be specified from the command line:
     *
     * ```
     * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.size=medium
     * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.foo=bar
     * ```
     */
    @get:Incubating
    val instrumentationRunnerArguments: MutableMap<String, String>

    /**
     * See [instrumentation](http://developer.android.com/guide/topics/manifest/instrumentation-element.html).
     */
    @get:Incubating
    @set:Incubating
    var handleProfiling: Boolean?

    /**
     * See [instrumentation](http://developer.android.com/guide/topics/manifest/instrumentation-element.html).
     */
    @get:Incubating
    @set:Incubating
    var functionalTest: Boolean?

    /**
     * Disables animations during instrumented tests you run from the command line.
     *
     * If you set this property to `true`, running instrumented tests with Gradle from the command
     * line executes `am instrument` with the `--no-window-animation` flag.
     * By default, this property is set to `false`.
     *
     * This property does not affect tests that you run using Android Studio. To learn more about
     * running tests from the command line, see
     * [Test from the Command Line](https://d.android.com/studio/test/command-line.html).
     */
    @get:Incubating
    @set:Incubating
    var animationsDisabled: Boolean

    /**
     * Specifies code coverage is enabled for module tests of type AndroidTest.
     *
     * If enabled, prepares module class files for code coverage collection such as instrumenting
     * dependent library classes and module classes. This allows for code coverage reports to be
     * generated.
     */
    @get:Incubating
    @set:Incubating
    var enableCoverage: Boolean

    /**
     * Configures Gradle Managed Devices for use in testing with the Unified test platform.
     */
    @get:Incubating
    val managedDevices: ManagedDevices

    /**
     * Configures Gradle Managed Devices for use in testing with the Unified test platform.
     */
    @Incubating
    fun managedDevices(action: ManagedDevices.() -> Unit)

    /**
     * Specifies whether to use on-device test orchestration.
     *
     * If you want to [use Android Test Orchestrator](https://developer.android.com/training/testing/junit-runner.html#using-android-test-orchestrator)
     * you need to specify `"ANDROID_TEST_ORCHESTRATOR"`, as shown below.
     * By default, this property is set to `"HOST"`, which disables on-device orchestration.
     */
    @get:Incubating
    @set:Incubating
    var execution: String

    /**
     * Configures Android Emulator Grpc Access
     *
     * Android Emulator Grpc Access will make it possible to interact with the emulator over gRPC
     *
     * ```
     * emulatorControl {
     *   enable true
     *   secondsValid 180
     *   allowedEndpoints.addAll(
     *       "/android.emulation.control.EmulatorController/getStatus",
     *       "/android.emulation.control.EmulatorController/getVmState")
     * }
     * ```
     */
    @get:Incubating
    val emulatorControl: EmulatorControl

    @Incubating
    fun emulatorControl(action: EmulatorControl.() -> Unit)

    /**
     * Configures Android Test Retention.
     *
     * Android Test Retention automatically takes emulator snapshots on test failures. It can only
     * work with Unified Test Platform (UTP).
     *
     * ```
     * emulatorSnapshots {
     *   enableForTestFailures true
     *   maxSnapshotsForTestFailures 2
     *   compressSnapshots false
     * }
     * ```
     */
    @get:Incubating
    val emulatorSnapshots: EmulatorSnapshots

    @Incubating
    fun emulatorSnapshots(action: EmulatorSnapshots.() -> Unit)

    /**
     * Specifies options for the
     * [Android Debug Bridge (ADB)](https://developer.android.com/studio/command-line/adb.html),
     * such as APK installation options.
     *
     * For more information about the properties you can configure in this block, see [Installation].
     */
    @get:Incubating
    val installation: Installation

    /**
     * Specifies options for the
     * [Android Debug Bridge (ADB)](https://developer.android.com/studio/command-line/adb.html),
     * such as APK installation options.
     *
     * For more information about the properties you can configure in this block, see [Installation].
     */
    @Incubating
    fun installation(action: Installation.() -> Unit)

    /**
     * Encapsulates signing configurations that you can apply to test APK
     *
     * For more information about the properties you can configure in this block,
     * see [ApkSigningConfig].
     */
    @get:Incubating
    val signing: ApkSigningConfig

    @Incubating
    fun signing(action: ApkSigningConfig.() -> Unit)

    @get:Incubating
    val multidex: MultiDexConfig

    @Incubating
    fun multidex(action: MultiDexConfig.() -> Unit)
}
