/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/**
 * Runs instrumentation tests of a variant on a device defined in the DSL.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class DeviceSerialTestTask: DefaultTask() {
    @Option(
        option = "serial",
        description = "The serial of the device to test against. This will take precedence over " +
                "the serials specified in the ANDROID_SERIAL environment variable. In addition, " +
                "when this argument is specified the test task will fail if it cannot connect to " +
                "the device. \n\n" +
                "Multiple devices can be specified by specifying the command multiple times. " +
                "i.e. myDeviceSerialTestTask --serial deviceSerial1 --serial deviceSerial2")
    fun setSerialOption(serials: List<String>) = serialValues.addAll(serials)

    @get: Input
    abstract val serialValues: ListProperty<String>
}
