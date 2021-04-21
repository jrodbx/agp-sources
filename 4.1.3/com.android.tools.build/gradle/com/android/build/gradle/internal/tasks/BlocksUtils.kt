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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.profile.TaskProfilingRecord
import com.android.ide.common.workers.GradlePluginMBeans
import com.android.tools.build.gradle.internal.profile.GradleTransformExecutionType
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import com.google.wireless.android.sdk.stats.GradleTransformExecution
import java.time.Duration

fun recordArtifactTransformSpan(projectName: String,
    type: GradleTransformExecutionType,
    block: () -> Unit) {

    val profileMBean = GradlePluginMBeans.getProfileMBean(projectName)
    val timeStart = TaskProfilingRecord.clock.instant()
    try {
        block.invoke()
    } finally {
        profileMBean?.registerSpan(
            null,
            GradleBuildProfileSpan.newBuilder()
                .setThreadId(Thread.currentThread().id)
                .setType(GradleBuildProfileSpan.ExecutionType.ARTIFACT_TRANSFORM)
                .setTransform(
                    GradleTransformExecution.newBuilder()
                        .setType(type.getNumber())
                )
                .setStartTimeInMs(timeStart.toEpochMilli())
                .setDurationInMs(
                    Duration.between(
                        timeStart,
                        TaskProfilingRecord.clock.instant()
                    )
                        .toMillis()
                )
        )
    }
}