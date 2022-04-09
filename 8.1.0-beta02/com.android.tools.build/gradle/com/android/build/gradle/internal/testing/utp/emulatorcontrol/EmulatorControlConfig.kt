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

package com.android.build.gradle.internal.testing.utp

import com.android.build.gradle.internal.dsl.EmulatorControl
import com.android.build.gradle.options.ProjectOptions
import java.io.Serializable

// Information needed to access the emulator from within the tests.
data class EmulatorControlConfig(
    val enabled: Boolean, val allowedEndpoints: Set<String>, val secondsValid: Int
) : Serializable

fun createEmulatorControlConfig(
    projectOptions: ProjectOptions, emulatorControl: EmulatorControl
): EmulatorControlConfig {
    return EmulatorControlConfig(
        emulatorControl.enable,
        emulatorControl.allowedEndpoints.toSet(),
        emulatorControl.secondsValid
    )
}
