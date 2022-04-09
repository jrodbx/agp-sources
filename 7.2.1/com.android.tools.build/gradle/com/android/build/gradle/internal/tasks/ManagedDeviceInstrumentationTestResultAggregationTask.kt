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

import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.testing.utp.UtpTestSuiteResultMerger
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import java.io.File
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

/**
 * Aggregates UTP test result protos into one.
 */
@DisableCachingByDefault
abstract class ManagedDeviceInstrumentationTestResultAggregationTask: NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputTestResultProtos: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputTestResultProto: RegularFileProperty

    override fun doTaskAction() {
        val resultProtos = inputTestResultProtos.filter(File::exists)
        if (!resultProtos.isEmpty) {
            val resultMerger = UtpTestSuiteResultMerger()
            resultProtos.forEach { resultProtoFile ->
                val proto = TestSuiteResult.parseFrom(resultProtoFile.inputStream())
                resultMerger.merge(proto)
            }

            val mergedTestResultPbFile = outputTestResultProto.get().asFile
            resultMerger.result.writeTo(mergedTestResultPbFile.outputStream())
            logger.lifecycle(
                "\nTest results saved as ${mergedTestResultPbFile.toURI()}.\n" +
                "Inspect these results in Android Studio by selecting Run > Import Tests " +
                "From File from the menu bar and importing test-result.pb."
            )
        }
    }

    class CreationAction(
        creationConfig: VariantCreationConfig,
        private val deviceTestResultFiles: List<File>,
        private val testResultOutputFile: File,
    ) : VariantTaskCreationAction<
            ManagedDeviceInstrumentationTestResultAggregationTask,
            VariantCreationConfig>(creationConfig) {

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
        }

        override fun configure(task: ManagedDeviceInstrumentationTestResultAggregationTask) {
            super.configure(task)

            task.inputTestResultProtos.from(deviceTestResultFiles)
        }
    }
}
