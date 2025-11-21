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

package com.android.build.api.dsl

import org.gradle.api.Incubating

/**
 * Options for configuring Android Test Retention.
 *
 * When enabled, Android Test Retention automatically takes emulator snapshots on test failures.
 */
@Incubating
interface EmulatorSnapshots {
    /** Enables automated test failure snapshots. Default to false. */
    var enableForTestFailures: Boolean

    /**
     * Call this function to take unlimited number of test failure snapshots (will ignore
     * maxSnapshotsForTestFailures setting)
     */
    fun retainAll()

    /**
     *  Maximum number of failures that would be snapshotted. Any failures after the first
     *  $maxSnapshotsForTestFailures will not have snapshots. Default to 2. Must be >0
     */
    var maxSnapshotsForTestFailures: Int

    /** Enables snapshot compression. Default to false. */
    var compressSnapshots: Boolean
}
