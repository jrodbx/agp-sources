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

import com.android.build.gradle.internal.profile.AnalyticsService.Params
import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.builder.profile.AnalyticsProfileWriter
import com.android.builder.profile.NameAnonymizerSerializer
import com.android.builder.profile.Recorder
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleBuildMemorySample
import com.google.wireless.android.sdk.stats.GradleBuildProfile
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import com.google.wireless.android.sdk.stats.GradleBuildProject
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import com.google.wireless.android.sdk.stats.GradleTransformExecution
import org.gradle.api.Project
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject

/**
 * [AnalyticsService] records execution spans of tasks and workers. At the end of the build,
 * it combines data from execution and configuration, and writes build proto to disk.
 *
 * The [Params] is configured with analytics collected at configuration, which can be serialized
 * in non-configuration cached run, and de-serialized in configuration cached run to instantiate
 * [AnalyticsService].
 *
 * In a single non-composite build, there can be only one instance of [AnalyticsService] per class
 * loader. In the context of composite build, for each build, there can be only one instance of
 * [AnalyticsService] per class loader.
 */
abstract class AnalyticsService :
    AnalyticsServiceApi, BuildService<Params>, AutoCloseable, OperationCompletionListener
{
    interface Params : BuildServiceParameters {
        val profile: Property<String>
        val anonymizer: Property<String>
        val projects: MapProperty<String, ProjectData>
        val enableProfileJson: Property<Boolean>
        val profileDir: Property<File?>
        val taskMetadata: MapProperty<String, TaskMetadata>
        val rootProjectPath: Property<String>
    }

    @get:Inject
    abstract val provider: ProviderFactory

    private val resourceManager: AnalyticsResourceManager = initializeResourceManager()

    init {
        initializeUsageTracker()
    }

    protected open fun initializeResourceManager(): AnalyticsResourceManager {
        return AnalyticsResourceManager(
            reconstructProfileBuilder(),
            ConcurrentHashMap(parameters.projects.get()),
            parameters.enableProfileJson.get(),
            parameters.profileDir.orNull,
            ConcurrentHashMap(parameters.taskMetadata.get()),
            parameters.rootProjectPath.get(),
            NameAnonymizerSerializer().fromJson(parameters.anonymizer.get())
        )
    }

    protected open fun initializeUsageTracker() {
        // Initialize UsageTracker because some tasks(e.g. lint) need to record analytics with
        // UsageTracker.
        AnalyticsProfileWriter().initializeUsageTracker()
    }

    override fun close() {
        resourceManager.writeAndFinish()
    }

    override fun onFinish(finishEvent: FinishEvent?) {
        resourceManager.recordTaskExecutionSpan(finishEvent)
    }

    override fun workerAdded(taskPath: String, workerKey: String) {
        getTaskRecord(taskPath)?.addWorker(workerKey)
    }

    override fun workerStarted(taskPath: String, workerKey: String) {
        val workerRecord = getWorkerRecord(taskPath, workerKey)
        workerRecord?.executionStarted()
    }

    override fun workerFinished(taskPath: String, workerKey: String) {
        val workerRecord = getWorkerRecord(taskPath, workerKey)
        if (workerRecord != null) {
            workerRecord.executionFinished()
            getTaskRecord(taskPath)?.workerFinished(workerRecord)
        }
    }

    override fun registerSpan(taskPath: String, builder: GradleBuildProfileSpan.Builder) {
        val taskRecord = getTaskRecord(taskPath)
        taskRecord?.addSpan(builder)
    }

    @Synchronized
    override fun getProjectBuillder(projectPath: String): GradleBuildProject.Builder? {
        return resourceManager.getProjectBuilder(projectPath)
    }

    @Synchronized
    override fun getVariantBuilder(
        projectPath: String,
        variantName: String
    ) : GradleBuildVariant.Builder? {
        return resourceManager.getVariantBuilder(projectPath, variantName)
    }

    @Synchronized
    override fun getTaskRecord(taskPath: String): TaskProfilingRecord? {
        return resourceManager.getTaskRecord(taskPath)
    }

    override fun recordBlock(
        executionType: GradleBuildProfileSpan.ExecutionType,
        transform: GradleTransformExecution?,
        projectPath: String,
        variantName: String,
        block: Recorder.VoidBlock
    ) {
        resourceManager.recordBlockAtExecution(
            executionType,
            transform,
            projectPath,
            variantName,
            block
        )
    }

    override fun setConfigurationSpans(spans: ConcurrentLinkedQueue<GradleBuildProfileSpan>) {
        resourceManager.configurationSpans.addAll(spans)
    }

    override fun setInitialMemorySampleForConfiguration(sample: GradleBuildMemorySample) {
        resourceManager.initialMemorySample = sample
    }

    override fun recordEvent(event: AndroidStudioEvent.Builder) {
        resourceManager.recordEvent(event)
    }

    override fun recordApplicationId(metadataFile: File) {
        resourceManager.recordApplicationId(metadataFile)
    }

    class RegistrationAction(project: Project)
        : ServiceRegistrationAction<AnalyticsService, Params>(
        project,
        AnalyticsService::class.java
    ) {
        override fun configure(parameters: Params) {}
    }

    private fun getWorkerRecord(taskPath: String, worker: String): WorkerProfilingRecord? {
        return getTaskRecord(taskPath)?.get(worker)
    }

    private fun reconstructProfileBuilder() : GradleBuildProfile.Builder {
        return GradleBuildProfile
            .parseFrom(Base64.getDecoder().decode(parameters.profile.get()))
            .toBuilder()
    }
}
