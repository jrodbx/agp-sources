/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.configureVariantProperties
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.symbols.generateRPackageClass
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import com.android.tools.r8.origin.Origin
import com.android.utils.FileUtils
import com.google.common.util.concurrent.MoreExecutors
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.DEXING)
abstract class PrivacySandboxSdkGenerateRPackageDexTask: NonIncrementalTask() {

    @get:Input
    abstract val applicationId: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(WorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.applicationId.set(applicationId)
            it.outputDir.set(outputDir)
        }
    }

    abstract class Params: ProfileAwareWorkAction.Parameters() {
        abstract val applicationId: Property<String>
        abstract val outputDir: DirectoryProperty
    }

    abstract class WorkAction: ProfileAwareWorkAction<Params>() {

        @get:Inject
        abstract val workerExecutor: WorkerExecutor

        override fun run() {
            val applicationId = parameters.applicationId.get()
            val outputDirectory = parameters.outputDir.get().asFile
            FileUtils.cleanOutputDir(outputDirectory)
            val (_,bytes) = generateRPackageClass(applicationId, 0x7F000000)

            val command = D8Command.builder().apply {
                addClassProgramData(bytes, Origin.unknown())
                disableDesugaring = true
                includeClassesChecksum = false
                minApiLevel = 14 // Minimum version supported by Android Jetpack
                mode = CompilationMode.RELEASE
                setOutput(outputDirectory.toPath(), OutputMode.DexIndexed)
                setIntermediate(false)
            }.build()
            D8.run(command, MoreExecutors.newDirectExecutorService())
        }
    }

    class CreationAction constructor(
        private val creationConfig: PrivacySandboxSdkVariantScope,
    ) : TaskCreationAction<PrivacySandboxSdkGenerateRPackageDexTask>() {

        override val name = "generateRPackageDex"
        override val type = PrivacySandboxSdkGenerateRPackageDexTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<PrivacySandboxSdkGenerateRPackageDexTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                PrivacySandboxSdkGenerateRPackageDexTask::outputDir
            ).on(PrivacySandboxSdkInternalArtifactType.R_PACKAGE_DEX)
        }

        override fun configure(task: PrivacySandboxSdkGenerateRPackageDexTask) {
            task.configureVariantProperties("", task.project.gradle.sharedServices)
            task.applicationId.setDisallowChanges(creationConfig.bundle.applicationId)
        }
    }
}
