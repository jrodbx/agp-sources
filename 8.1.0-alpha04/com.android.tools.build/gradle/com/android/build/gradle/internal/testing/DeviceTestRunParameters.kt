/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.testing

import com.android.build.api.instrumentation.manageddevice.DeviceTestRunInput
import org.gradle.api.file.RegularFileProperty

data class DeviceTestRunParameters<T: DeviceTestRunInput> (

        override val deviceInput: T,

        override val setupResult: RegularFileProperty,

        override val testRunData: TestRunData
): com.android.build.api.instrumentation.manageddevice.DeviceTestRunParameters<T>
