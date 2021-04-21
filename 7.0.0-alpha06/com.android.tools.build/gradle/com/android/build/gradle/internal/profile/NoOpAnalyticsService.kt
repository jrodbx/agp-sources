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
import com.google.wireless.android.sdk.stats.GradleBuildProfile
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import com.google.wireless.android.sdk.stats.GradleBuildProject
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import com.google.wireless.android.sdk.stats.GradleTransformExecution
import org.gradle.tooling.events.FinishEvent
import java.util.concurrent.ConcurrentHashMap
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * No-op implementation of [AnalyticsService], which is used when analytics is disabled.
 */
abstract class NoOpAnalyticsService : AnalyticsService() {

    override fun initializeUsageTracker() {}

    override fun initializeResourceManager(): AnalyticsResourceManager {
        return AnalyticsResourceManager(
            GradleBuildProfile.newBuilder(),
            ConcurrentHashMap(),
            false,
            null,
            ConcurrentHashMap(),
            null
        )
    }

    override fun close() {}

    override fun getProjectBuillder(projectPath: String): GradleBuildProject.Builder? = null

    override fun getVariantBuilder(
        projectPath: String,
        variantName: String
    ): GradleBuildVariant.Builder? {
        return null
    }

    override fun onFinish(finishEvent: FinishEvent?) {}

    override fun getTaskRecord(taskPath: String): TaskProfilingRecord? = null

    override fun recordBlock(
        executionType: GradleBuildProfileSpan.ExecutionType,
        transform: GradleTransformExecution?,
        projectPath: String,
        variantName: String,
        block: Recorder.VoidBlock
    ) {
        block.call()
    }

    override fun recordEvent(event: AndroidStudioEvent.Builder) {}

    override fun registerSpan(taskPath: String, builder: GradleBuildProfileSpan.Builder) {}

    override fun setConfigurationSpans(spans: ConcurrentLinkedQueue<GradleBuildProfileSpan>) {}

    override fun setInitialMemorySampleForConfiguration(sample: GradleBuildMemorySample) {}

    override fun workerAdded(taskPath: String, workerKey: String) {}

    override fun workerFinished(taskPath: String, workerKey: String) {}

    override fun workerStarted(taskPath: String, workerKey: String) {}

    override fun recordApplicationId(metadataFile: File) {}
}