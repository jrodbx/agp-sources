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

package com.android.build.gradle.internal.testing.utp

import com.android.build.gradle.internal.dsl.EmulatorSnapshots
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.OptionalBooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.google.common.base.Preconditions
import java.io.Serializable

/**
 * A data class to hold the vaules from android.testOptions.emulatorSnapshots, as documented here:
 * https://docs.google.com/document/d/1IjPjvWK-qVC4U5XmYcTthL_OXidH2nm2GXANLhiuf9E/edit?usp=sharing
 */
data class RetentionConfig(
    val enabled: Boolean,
    val retainAll: Boolean,
    val compressSnapshots: Boolean,
    val maxSnapshots: Int
) : Serializable

fun createRetentionConfig(
    projectOptions: ProjectOptions,
    emulatorSnapshots: EmulatorSnapshots
): RetentionConfig {
    var enableFailureRetention = emulatorSnapshots.enableForTestFailures
    var retainAll = emulatorSnapshots.getRetainAll()
    var maxSnapshots = emulatorSnapshots.maxSnapshotsForTestFailures
    // Overriding failure retention configs by the retention flag.
    projectOptions.get(IntegerOption.TEST_FAILURE_RETENTION)?.let { failureRetentionValue ->
        if (failureRetentionValue > 0) {
            enableFailureRetention = true
            retainAll = false
            maxSnapshots = failureRetentionValue
        } else if (failureRetentionValue == 0) {
            enableFailureRetention = false
        } else {
            enableFailureRetention = true
            retainAll = true
        }
    }
    Preconditions.checkArgument(
        !enableFailureRetention || retainAll || maxSnapshots > 0,
        "android.emulatorSnapshots.maxSnapshotsForTestFailures should be >0, actual value "
                + emulatorSnapshots.maxSnapshotsForTestFailures
    )
    val compressSnapshots = projectOptions.get(
        OptionalBooleanOption.ENABLE_TEST_FAILURE_RETENTION_COMPRESS_SNAPSHOT
    ) ?: emulatorSnapshots.compressSnapshots
    return RetentionConfig(enableFailureRetention, retainAll, compressSnapshots, maxSnapshots)
}
