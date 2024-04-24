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

package com.android.build.api.instrumentation

import org.gradle.api.Incubating
import org.gradle.api.Project
import org.gradle.workers.WorkerExecutor

/**
 * An interface for a class that can construct a [ManagedDeviceTestRunner]
 * for a Gradle Managed Device test.
 *
 * If you work on creating a new device type of Gradle Managed Device,
 * your implementation class of the Gradle Managed Device DSL interface has to
 * implement this interface.
 *
 * @suppress Do not use from production code. All properties in this interface are exposed for
 * prototype.
 */
@Incubating
interface ManagedDeviceTestRunnerFactory {

    /**
     * Constructs a [ManagedDeviceTestRunner] that is to be used for running
     * a test.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @Incubating
    fun createTestRunner(
        project: Project,
        workerExecutor: WorkerExecutor,
        useOrchestrator: Boolean,
        enableEmulatorDisplay: Boolean,
    ): ManagedDeviceTestRunner
}
