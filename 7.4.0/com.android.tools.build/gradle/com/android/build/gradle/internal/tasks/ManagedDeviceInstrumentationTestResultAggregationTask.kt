/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.gradle.internal.component.InstrumentedTestCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.test.report.ReportType
import com.android.build.gradle.internal.test.report.TestReport
import com.android.build.gradle.internal.testing.utp.UtpTestSuiteResultMerger
import com.android.build.gradle.internal.tasks.TaskCategory
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import java.io.File
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.work.DisableCachingByDefault

/**
 * Aggregates UTP test result protos into one.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class ManagedDeviceInstrumentationTestResultAggregationTask: NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputTestResultProtos: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputTestResultProto: RegularFileProperty

    @get:OutputDirectory
    abstract val outputTestReportHtmlDir: DirectoryProperty

    override fun doTaskAction() {
        val resultProtos = inputTestResultProtos.filter(File::exists)
        if (!resultProtos.isEmpty) {
            val resultMerger = UtpTestSuiteResultMerger()
            resultProtos.forEach { resultProtoFile ->
                val proto = resultProtoFile.inputStream().use {
                    TestSuiteResult.parseFrom(it)
                }
                resultMerger.merge(proto)
            }

            val mergedTestResultPbFile = outputTestResultProto.get().asFile
            mergedTestResultPbFile.outputStream().use {
                resultMerger.result.writeTo(it)
            }
        }

        TestReport(
            ReportType.SINGLE_FLAVOR,
            inputTestResultProtos.mapNotNull(File::getParentFile).filter(File::exists).toList(),
            outputTestReportHtmlDir.get().asFile
        ).generateReport()

        val reportUrl = ConsoleRenderer().asClickableFileUrl(
            File(outputTestReportHtmlDir.get().asFile, "index.html"))
        logger.lifecycle("Test execution completed. See the report at: $reportUrl")
    }

    class CreationAction(
        creationConfig: InstrumentedTestCreationConfig,
        private val deviceTestResultFiles: List<File>,
        private val testResultOutputFile: File,
        private val testReportHtmlOutputDir: File,
    ) : VariantTaskCreationAction<
            ManagedDeviceInstrumentationTestResultAggregationTask,
            InstrumentedTestCreationConfig>(creationConfig) {

        override val name: String
            get() = computeTaskName("merge", "TestResultProtos")

        override val type: Class<ManagedDeviceInstrumentationTestResultAggregationTask>
            get() = ManagedDeviceInstrumentationTestResultAggregationTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<ManagedDeviceInstrumentationTestResultAggregationTask>) {
            creationConfig.artifacts
                .setInitialProvider(
                    taskProvider,
                    ManagedDeviceInstrumentationTestResultAggregationTask::outputTestResultProto)
                .withName(testResultOutputFile.name)
                .atLocation(testResultOutputFile.parentFile.absolutePath)
                .on(InternalArtifactType.MANAGED_DEVICE_ANDROID_TEST_MERGED_RESULTS_PROTO)

            creationConfig.artifacts
                .setInitialProvider(
                    taskProvider,
                    ManagedDeviceInstrumentationTestResultAggregationTask::outputTestReportHtmlDir)
                .withName("allDevices")
                .atLocation(testReportHtmlOutputDir.absolutePath)
                .on(InternalArtifactType.MANAGED_DEVICE_ANDROID_TEST_MERGED_RESULTS_REPORT)
        }

        override fun configure(task: ManagedDeviceInstrumentationTestResultAggregationTask) {
            super.configure(task)

            task.inputTestResultProtos.from(deviceTestResultFiles)
        }
    }
}
