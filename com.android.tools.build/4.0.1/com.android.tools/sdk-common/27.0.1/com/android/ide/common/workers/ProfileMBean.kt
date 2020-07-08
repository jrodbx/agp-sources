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

package com.android.ide.common.workers

import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import java.time.Duration
import java.time.Instant

/**
 * MBean for services related to profiling notifications.
 *
 */
interface ProfileMBean {
    /**
     * Notification of the start of execution of a worker.
     * @param taskPath spawning task identification
     * @param workerKey worker identification.
     */
    fun workerStarted(taskPath: String, workerKey: String)

    /**
     * Notification of the completion of execution of a worker.
     * @param taskPath spawning task identification
     * @param workerKey worker identification.
     */
    fun workerFinished(taskPath: String, workerKey: String)

    /**
     * Task/worker/thread span registration. Will use the current
     * parent as the anchor.
     *
     * @param taskPath spawning task path identification or null if the span is not attached
     * to any task in particular.
     * @param builder the [GradleBuildProfileSpan] builder
     */
    fun registerSpan(taskPath: String?, spanBuilder: GradleBuildProfileSpan.Builder)
}
