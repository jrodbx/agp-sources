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

package com.android.build.gradle.internal.profile

import com.android.builder.profile.Recorder
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleBuildMemorySample
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import com.google.wireless.android.sdk.stats.GradleBuildProject
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import com.google.wireless.android.sdk.stats.GradleTransformExecution
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * An interface describes all APIs exposed by [AnalyticsService]
 */
interface AnalyticsServiceApi {

    /**
     * Notification of a new worker execution request.
     * @param taskPath spawning task identification
     * @param workerKey worker identification.
     */
    fun workerAdded(taskPath: String, workerKey: String)

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
     * Task span registration. Will use the current parent as the anchor.
     *
     * @param taskPath spawning task path identification
     * @param builder the [GradleBuildProfileSpan] builder
     */
    fun registerSpan(taskPath: String, builder: GradleBuildProfileSpan.Builder)

    /**
     * Provide [GradleBuildProject.Builder] for tasks to write statistics at execution time
     */
    fun getProjectBuillder(projectPath: String): GradleBuildProject.Builder?

    /**
     * Provide [GradleBuildVariant.Builder] for tasks to write statistics at execution time
     */
    fun getVariantBuilder(projectPath: String, variantName: String) : GradleBuildVariant.Builder?

    /**
     * Search for [TaskProfilingRecord] based on task path, a new one is created if not found.
     */
    fun getTaskRecord(taskPath: String): TaskProfilingRecord?

    /**
     * Records the time elapsed while executing a [Recorder.VoidBlock] and append the span to the
     * build profile
     */
    fun recordBlock(
        executionType: GradleBuildProfileSpan.ExecutionType,
        transform: GradleTransformExecution?,
        projectPath: String,
        variantName: String,
        block: Recorder.VoidBlock
    )

    /**
     * Record an android studio event
     */
    fun recordEvent(event: AndroidStudioEvent.Builder)

    /**
     * In non-configuration cached run, we records time for configuring project and creating base
     * extension & tasks. Those spans are passed from [AnalyticsConfiguratorService]
     * to [AnalyticsService] at the end of configuration when [AnalyticsService] is instantiated.
     */
    fun setConfigurationSpans(spans: ConcurrentLinkedQueue<GradleBuildProfileSpan>)

    /**
     * In non-configuration cached run, the initial memory sample is collected at the beginning of
     * configuration phase and passed from [AnalyticsConfiguratorService] to [AnalyticsService]
     * at the end of configuration when [AnalyticsService] is instantiated.
     */
    fun setInitialMemorySampleForConfiguration(sample: GradleBuildMemorySample)

    fun recordApplicationId(metadataFile: File)
}
