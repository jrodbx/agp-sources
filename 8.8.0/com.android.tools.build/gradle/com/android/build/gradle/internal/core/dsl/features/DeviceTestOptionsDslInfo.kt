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

package com.android.build.gradle.internal.core.dsl.features

import com.android.build.api.dsl.EmulatorControl
import com.android.build.api.dsl.EmulatorSnapshots
import com.android.build.api.dsl.ManagedDevices

/**
 * Contains the final dsl info computed from the extension level DSL object model that are needed
 * by components that configure and run instrumentation tests
 */
interface DeviceTestOptionsDslInfo: TestOptionsDslInfo {
    val animationsDisabled: Boolean
    val execution: String
    val resultsDir: String?
    val reportDir: String?
    val managedDevices: ManagedDevices
    val emulatorControl: EmulatorControl
    val emulatorSnapshots: EmulatorSnapshots
    val codeCoverageEnabled:Boolean
}
