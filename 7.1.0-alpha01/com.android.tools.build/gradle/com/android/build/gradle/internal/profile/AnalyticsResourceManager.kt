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

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.isConfigurationCache
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.VariantAwareTask
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptionService
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.android.builder.profile.AnalyticsProfileWriter
import com.android.builder.profile.NameAnonymizer
import com.android.builder.profile.NameAnonymizerSerializer
import com.android.builder.profile.Recorder
import com.android.builder.profile.ThreadRecorder
import com.android.tools.analytics.Anonymizer
import com.android.tools.analytics.CommonMetricsData
import com.android.tools.build.gradle.internal.profile.GradleTaskExecutionType
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Strings
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleBuildMemorySample
import com.google.wireless.android.sdk.stats.GradleBuildProfile
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import com.google.wireless.android.sdk.stats.GradleBuildProject
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import com.google.wireless.android.sdk.stats.GradleTransformExecution
import org.gradle.api.Project
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.ProviderFactory
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskSuccessResult
import java.io.File
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.nio.file.Path
import java.util.Base64
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * [AnalyticsResourceManager] manages all build profile data for [AnalyticsConfiguratorService] and
 * [AnalyticsService].
 */
class AnalyticsResourceManager constructor(
    private val profileBuilder: GradleBuildProfile.Builder,
    private val projects: ConcurrentHashMap<String, ProjectData>,
    private var enableProfileJson: Boolean,
    private var profileDir: File?,
    private val taskMetadata: ConcurrentHashMap<String, TaskMetadata>,
    private var rootProjectPath: String?,
    private val nameAnonymizer: NameAnonymizer = NameAnonymizer(),
) {
    var initialMemorySample = createMemorySample()
    val configurationSpans = ConcurrentLinkedQueue<GradleBuildProfileSpan>()

    @VisibleForTesting
    val executionSpans = ConcurrentLinkedQueue<GradleBuildProfileSpan>()
    private val applicationIds = ConcurrentLinkedQueue<String>()

    private var lastRecordId: AtomicLong? = null
    private val taskRecords = ConcurrentHashMap<String, TaskProfilingRecord>()
    private val otherEvents =
        Collections.synchronizedList<AndroidStudioEvent.Builder>(mutableListOf())
    private val threadRecorder: Recorder = ThreadRecorder()
    private val analyticsWriter = AnalyticsProfileWriter()

    fun writeAndFinish() {
        analyticsWriter.writeAndFinish(
            getFinalProfile(),
            otherEvents,
            profileDir,
            enableProfileJson
        )
    }

    /**
     * Get [GradleBuildProject.Builder] at configuration or execution phase.
     */
    fun getProjectBuilder(projectPath: String) : GradleBuildProject.Builder {
        return projects.computeIfAbsent(projectPath) {
            val projectBuilder = GradleBuildProject.newBuilder()
            projectBuilder.id = nameAnonymizer.anonymizeProjectPath(it)
            ProjectData(projectBuilder)
        }.projectBuilder
    }

    /**
     * Get [GradleBuildVariant.Builder] at configuration or execution phase
     */
    fun getVariantBuilder(projectPath: String, variantName: String) : GradleBuildVariant.Builder {
        val projectData: ProjectData = projects.computeIfAbsent(projectPath) {
            val projectBuilder = GradleBuildProject.newBuilder()
            projectBuilder.id = nameAnonymizer.anonymizeProjectPath(it)
            ProjectData(projectBuilder)
        }

        return projectData.variantBuilders.computeIfAbsent(variantName) {
            val variantBuilder = GradleBuildVariant.newBuilder()
            variantBuilder.id = nameAnonymizer.anonymizeVariant(projectPath, it)
            variantBuilder
        }
    }

    /**
     * Get [TaskProfilingRecord] at execution phase
     */
    fun getTaskRecord(taskPath: String) : TaskProfilingRecord? {
        if (!taskRecords.containsKey(taskPath)) {
            val builder = GradleBuildProfileSpan.newBuilder().apply {
                type = GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION
                id = allocateRecordId()
                threadId = Thread.currentThread().id
            }

            val projectPath = getProjectPath(taskPath) ?: return null
            val variantName = getVariantName(taskPath)

            taskRecords[taskPath] = TaskProfilingRecord(
                this,
                builder,
                taskPath,
                projectPath,
                variantName
            )
        }
        return taskRecords[taskPath]
    }

    /**
     * Create and write a task execution span when receiving a task finish event from Gradle
     */
    fun recordTaskExecutionSpan(finishEvent: FinishEvent?) {
        if (finishEvent == null || finishEvent !is TaskFinishEvent) {
            return
        }
        val taskPath = finishEvent.descriptor.taskPath
        val taskRecord = getTaskRecord(taskPath) ?: return
        val typeName = getTypeName(taskPath) ?: return
        val taskType = getTaskExecutionType(typeName)
        val taskResult = finishEvent.result

        taskRecord.spanBuilder.taskBuilder
            .setType(taskType.number)
            .setDidWork(taskResult !is TaskSkippedResult)
            .setSkipped(taskResult is TaskSkippedResult)
            .setUpToDate(taskResult is TaskSuccessResult && taskResult.isUpToDate)
            .setFailed(taskResult is TaskFailureResult)

        taskRecord.setTaskStartTime(taskResult.startTime)
        taskRecord.setTaskEndTime(taskResult.endTime)

        // all workers must be done at this point
        taskRecord.writeTaskSpan()
        createAndRecordMemorySample()
    }

    /**
     * Allocate id for [GradleBuildProfileSpan] record at configuration or execution phase
     */
    @Synchronized
    fun allocateRecordId(): Long {
        // In non-configuration cached run, the id of execution spans increases from the largest
        // id of spans collected at configuration time.
        if (lastRecordId == null) {
            val spansCount = configurationSpans.size
            lastRecordId = AtomicLong((1 + spansCount).toLong())
        }
        return lastRecordId!!.incrementAndGet()
    }

    /**
     * Records the time elapsed while executing a void block at configuration phase and saves the
     * resulting [GradleBuildProfileSpan].
     */
    fun recordBlockAtConfiguration(
        executionType: GradleBuildProfileSpan.ExecutionType,
        projectPath: String,
        variant: String?,
        block: Recorder.VoidBlock
    ) {
        val span = threadRecorder.record(
            executionType,
            getProjectId(projectPath),
            getVariantId(projectPath, variant),
            allocateRecordId(),
            block)
        configurationSpans.add(span)
    }

    /**
     * Records the time elapsed while executing a void block at execution phase and saves the
     * resulting [GradleBuildProfileSpan].
     */
    fun recordBlockAtExecution(
        executionType: GradleBuildProfileSpan.ExecutionType,
        transform: GradleTransformExecution?,
        projectPath: String,
        variantName: String?,
        block: Recorder.VoidBlock
    ) {
        val span = threadRecorder.record(
            executionType,
            transform,
            getProjectId(projectPath),
            getVariantId(projectPath, variantName),
            allocateRecordId(),
            block)
        executionSpans.add(span)
    }

    /**
     * Used by [TaskProfilingRecord] to write task or worker span
     */
    fun writeRecord(
        projectPath: String,
        variantName: String?,
        executionRecord: GradleBuildProfileSpan.Builder,
        taskExecutionPhases: List<GradleBuildProfileSpan>
    ) {
        getProjectId(projectPath).let { executionRecord.project = it }
        if (variantName == null) {
            executionRecord.variant = NO_VARIANT_SPECIFIED
        } else {
            getVariantId(projectPath, variantName).let {
                executionRecord.variant = it }
        }
        executionSpans.add(executionRecord.build())
        if (taskExecutionPhases.isNotEmpty()) {
            val firstPhase = taskExecutionPhases[0]
            // add the gradle snapshot calculation span.
            executionSpans.add(
                GradleBuildProfileSpan.newBuilder()
                    .setType(GradleBuildProfileSpan.ExecutionType.GRADLE_PRE_TASK_SPAN)
                    .setParentId(executionRecord.id)
                    .setThreadId(executionRecord.threadId)
                    .setStartTimeInMs(executionRecord.startTimeInMs)
                    .setDurationInMs(
                        firstPhase.startTimeInMs
                                - executionRecord.startTimeInMs
                    )
                    .build()
            )
        }
        executionSpans.addAll(taskExecutionPhases)
    }

    fun configureAnalyticsService(params: AnalyticsService.Params) {
        params.profile.set(Base64.getEncoder().encodeToString(profileBuilder.build().toByteArray()))
        params.anonymizer.set(NameAnonymizerSerializer().toJson(nameAnonymizer))
        params.projects.set(projects)
        params.enableProfileJson.set(enableProfileJson)
        params.profileDir.set(profileDir)
        params.taskMetadata.set(taskMetadata)
        params.rootProjectPath.set(rootProjectPath)
    }

    fun recordGlobalProperties(project: Project) {
        val projectOptions = getBuildService<ProjectOptionService>(project.gradle.sharedServices)
            .get().projectOptions
        val providers = project.providers

        recordPlugins(project)

        profileBuilder
            .setOsName(getSystemProperty(providers,"os.name"))
            .setOsVersion(getSystemProperty(providers,"os.version"))
            .setJavaVersion(getSystemProperty(providers,"java.version"))
            .setJavaVmVersion(getSystemProperty(providers,"java.vm.version"))
            .setMaxMemory(Runtime.getRuntime().maxMemory())
            .setGradleVersion(project.gradle.gradleVersion)

        val configCachingEnabled = project.gradle.startParameter.isConfigurationCache
        if (configCachingEnabled != null) {
            profileBuilder.configurationCachingEnabled = configCachingEnabled
        }
        profileBuilder.parallelTaskExecution = project.gradle.startParameter.isParallelProjectExecutionEnabled

        // Use 'platform independent' path to match AS behaviour.
        rootProjectPath = project.rootProject.projectDir.absolutePath.replace('\\', '/')
        enableProfileJson = projectOptions.get(BooleanOption.ENABLE_PROFILE_JSON)
        profileDir = getProfileDir(projectOptions, project.gradle)?.toFile()
    }

    fun collectTaskMetadata(graph: TaskExecutionGraph) {
        for (task in graph.allTasks) {
            val variantName =
                if (task is VariantAwareTask) task.variantName
                else task.extensions.findByName(PROPERTY_VARIANT_NAME_KEY) as String?

            taskMetadata[task.path] = TaskMetadata(
                task.project.path,
                variantName,
                AnalyticsUtil.getPotentialTaskExecutionTypeName(task.javaClass))
        }
    }

    fun recordEvent(event: AndroidStudioEvent.Builder) {
        otherEvents.add(event)
    }

    fun recordApplicationId(metadataFile: File) {
        applicationIds.add(metadataFile.readText())
    }

    private fun getProjectId(projectPath: String) : Long {
        val id = projects[projectPath]?.projectBuilder?.id
        if (id != null) {
            return id
        }
        return nameAnonymizer.anonymizeProjectPath(projectPath)
    }

    private fun getVariantId(projectPath: String, variantName: String?) : Long {
        val variantId = projects[projectPath]?.variantBuilders?.get(variantName)?.id
        if (variantId != null) {
            return variantId
        }
        return nameAnonymizer.anonymizeVariant(projectPath, variantName)
    }

    private fun getProjectPath(taskPath: String) : String? {
        return taskMetadata[taskPath]?.projectPath
    }

    private fun getVariantName(taskPath: String) : String? {
        return taskMetadata[taskPath]?.variantName
    }

    private fun getTypeName(taskPath: String) : String? {
        return taskMetadata[taskPath]?.typeName
    }

    private fun recordPlugins(project: Project) {
        project.gradle.allprojects {
            val projectBuilder = getProjectBuilder(it.path)
            it.plugins.forEach { plugin ->
                projectBuilder.addPlugin(AnalyticsUtil.toProto(plugin))
                projectBuilder.addPluginNames(plugin.javaClass.name)
            }
        }
    }

    fun recordTaskNames(graph: TaskExecutionGraph) {
        for (task in graph.allTasks) {
            getProjectBuilder(task.project.path).addTaskNames(task.javaClass.name)
        }
    }

    private fun getTaskExecutionType(taskName: String): GradleTaskExecutionType {
        return try {
            GradleTaskExecutionType.valueOf(taskName)
        } catch (ignored: IllegalArgumentException) {
            GradleTaskExecutionType.UNKNOWN_TASK_TYPE
        }
    }

    private fun getSystemProperty(
        providerFactory: ProviderFactory,
        propertyName: String
    ): String {
        return providerFactory
            .systemProperty(propertyName)
            .forUseAtConfigurationTime()
            .orElse("")
            .get()
    }

    private fun getFinalProfile() : GradleBuildProfile {
        profileBuilder.addMemorySample(initialMemorySample)
        val endMemorySample = createAndRecordMemorySample()

        profileBuilder.addAllSpan(configurationSpans)
        profileBuilder.addAllSpan(executionSpans)

        projects.forEach {
            val projectBuilder = it.value.projectBuilder

            it.value.variantBuilders.forEach { entry ->
                projectBuilder.addVariant(entry.value)
            }
            profileBuilder.addProject(projectBuilder)
        }

        profileBuilder
            .setBuildTime(endMemorySample.timestamp - initialMemorySample.timestamp)
            .setGcCount(endMemorySample.gcCount - initialMemorySample.gcCount)
            .setGcTime(endMemorySample.gcTimeMs - initialMemorySample.gcTimeMs)

        val anonymizedProjectId =
            try {
                Anonymizer.anonymizeUtf8(
                    LoggerWrapper.getLogger(AnalyticsResourceManager::class.java), rootProjectPath)
            } catch (e: IOException) {
                "*ANONYMIZATION_ERROR*"
            }

        profileBuilder
            .addAllRawProjectId(applicationIds.toSet().toList().sorted())
            .setProjectId(anonymizedProjectId)

        return profileBuilder.build()
    }

    private fun createAndRecordMemorySample() : GradleBuildMemorySample {
        val stats = createMemorySample()
        synchronized(profileBuilder) {
            profileBuilder.addMemorySample(stats)
        }
        return stats
    }

    private fun createMemorySample(): GradleBuildMemorySample {
        return GradleBuildMemorySample.newBuilder()
            .setJavaProcessStats(CommonMetricsData.javaProcessStats)
            .setTimestamp(System.currentTimeMillis())
            .build()
    }

    private fun getProfileDir(projectOptions: ProjectOptions, gradle: Gradle): Path? {
        val profileDir = projectOptions.get(StringOption.PROFILE_OUTPUT_DIR)
        val enableJsonProfile = projectOptions.get(BooleanOption.ENABLE_PROFILE_JSON)
        return when {
            profileDir != null -> {
                gradle.rootProject.file(profileDir).toPath()
            }
            enableJsonProfile -> {
                // If profile json is enabled but no directory is given for the profile outputs,
                // default to build/android-profile
                gradle.rootProject
                    .buildDir
                    .toPath()
                    .resolve(PROFILE_DIRECTORY)
            }
            else -> {
                null
            }
        }
    }
}

/**
 * A wrapper class mainly to associate [GradleBuildProject.Builder] of a project with
 * [GradleBuildVariant.Builder]s of variants of that project
 */
class ProjectData(var projectBuilder: GradleBuildProject.Builder): Serializable {

    var variantBuilders: MutableMap<String, GradleBuildVariant.Builder> = mutableMapOf()

    private fun writeObject(objectOutputStream: ObjectOutputStream) {
        objectOutputStream.writeObject(
            Base64.getEncoder().encodeToString(projectBuilder.build().toByteArray())
        )
        val serializableVariantBuilders = variantBuilders.mapValues {
            Base64.getEncoder().encodeToString(it.value.build().toByteArray())
        }
        objectOutputStream.writeObject(serializableVariantBuilders)
    }

    private fun readObject(objectInputStream: ObjectInputStream) {
        val projectBuilderSerialized = objectInputStream.readObject() as String
        projectBuilder = GradleBuildProject.parseFrom(
            Base64.getDecoder().decode(projectBuilderSerialized)).toBuilder()
        val variantBuildersSerialized = objectInputStream.readObject() as Map<String, String>
        variantBuilders = variantBuildersSerialized.mapValues {
            GradleBuildVariant.parseFrom(Base64.getDecoder().decode(it.value)).toBuilder()
        }.toMutableMap()
    }
}

/**
 * A wrapper class of metadata associated with a task path
 */
data class TaskMetadata(
    val projectPath: String,
    val variantName: String?,
    val typeName: String
) : Serializable

/**
 * The default variant id for [GradleBuildProfileSpan] record when variantName is not specified
 */
const val NO_VARIANT_SPECIFIED = 0L

const val PROFILE_DIRECTORY = "android-profile"
const val PROPERTY_VARIANT_NAME_KEY = "AGP_VARIANT_NAME"
