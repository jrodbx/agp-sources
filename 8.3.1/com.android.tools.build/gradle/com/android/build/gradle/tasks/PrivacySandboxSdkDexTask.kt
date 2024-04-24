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

import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.DEFAULT_NUM_BUCKETS
import com.android.build.gradle.internal.tasks.DexArchiveBuilderTask
import com.android.build.gradle.internal.tasks.DexArchiveBuilderTaskDelegate
import com.android.build.gradle.internal.tasks.DexParameterInputs
import com.android.build.gradle.internal.tasks.NewIncrementalTask
import com.android.build.gradle.internal.tasks.configureVariantProperties
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.SyncOptions
import com.android.buildanalyzer.common.TaskCategory
import com.android.sdklib.AndroidVersion
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.Incremental
import org.gradle.work.InputChanges

@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.DEXING)
abstract class PrivacySandboxSdkDexTask: NewIncrementalTask() {

    @get:Incremental
    @get:Classpath
    abstract val classes: ConfigurableFileCollection

    @get:LocalState
    @get:Optional
    abstract val desugarGraphDir: DirectoryProperty

    @get:Nested
    abstract val dexParams: DexParameterInputs

    @get:LocalState
    abstract val inputJarHashesFile: RegularFileProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @get:OutputDirectory
    abstract val globalSyntheticsOutput: DirectoryProperty

    override fun doTaskAction(inputChanges: InputChanges) {
        DexArchiveBuilderTaskDelegate(
            isIncremental = inputChanges.isIncremental,
            projectClasses = classes.files,
            projectChangedClasses = DexArchiveBuilderTask.getChanged(
                canRunIncrementally = inputChanges.isIncremental,
                inputChanges = inputChanges,
                input = classes
            ),
            subProjectClasses = emptySet(),
            subProjectChangedClasses = emptySet(),
            externalLibClasses = emptySet(),
            externalLibChangedClasses = emptySet(),
            mixedScopeClasses = emptySet(),
            mixedScopeChangedClasses = emptySet(),
            projectOutputs = DexArchiveBuilderTaskDelegate.DexingOutputs(
                output.asFile.get(),
                globalSyntheticsOutput.asFile.get()
            ),
            subProjectOutputs = null,
            externalLibsOutputs = null,
            mixedScopeOutputs = null,
            dexParams = dexParams.toDexParameters(),
            desugarClasspathChangedClasses = DexArchiveBuilderTask.getChanged(
                inputChanges.isIncremental,
                inputChanges,
                dexParams.desugarClasspath
            ),
            desugarGraphDir = desugarGraphDir.get().asFile.takeIf { dexParams.withDesugaring.get() },
            inputJarHashesFile = inputJarHashesFile.asFile.get(),
            numberOfBuckets = DEFAULT_NUM_BUCKETS,
            workerExecutor = workerExecutor,
            projectPath = projectPath,
            taskPath = path,
            analyticsService = analyticsService
        ).doProcess()
    }

    class CreationAction(val creationConfig: PrivacySandboxSdkVariantScope)
        : TaskCreationAction<PrivacySandboxSdkDexTask>() {

        override val name: String
            get() = "dexClasses"
        override val type: Class<PrivacySandboxSdkDexTask>
            get() = PrivacySandboxSdkDexTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<PrivacySandboxSdkDexTask>) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                PrivacySandboxSdkDexTask::output
            ).on(PrivacySandboxSdkInternalArtifactType.DEX_ARCHIVE)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                PrivacySandboxSdkDexTask::inputJarHashesFile
            ).withName("out").on(PrivacySandboxSdkInternalArtifactType.DEX_ARCHIVE_INPUT_JAR_HASHES)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                PrivacySandboxSdkDexTask::desugarGraphDir
            ).withName("out").on(PrivacySandboxSdkInternalArtifactType.DESUGAR_GRAPH)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                PrivacySandboxSdkDexTask::globalSyntheticsOutput
            ).withName("out").on(PrivacySandboxSdkInternalArtifactType.GLOBAL_SYNTHETICS_ARCHIVE)
        }

        override fun configure(task: PrivacySandboxSdkDexTask) {
            task.configureVariantProperties("", task.project.gradle.sharedServices)
            task.classes.fromDisallowChanges(
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_CLASSES)
            )
            val minSdk = creationConfig.minSdkVersion.apiLevel
            task.dexParams.apply {
                debuggable.setDisallowChanges(false)
                // Enable desugaring by default as speed isn't a high priority for the privacy
                // sandbox SDK plugin
                withDesugaring.setDisallowChanges(true)

                enableApiModeling.setDisallowChanges(true)
                enableGlobalSynthetics.setDisallowChanges(true)

                minSdkVersion.setDisallowChanges(minSdk)
                errorFormatMode.setDisallowChanges(SyncOptions.ErrorFormatMode.HUMAN_READABLE)

                // If min sdk version for dexing is >= N(24) then we can avoid adding extra classes
                // to the desugar classpaths.
                if (minSdk < AndroidVersion.VersionCodes.N) {
                    desugarClasspath.from(
                        creationConfig.dependencies.getArtifactFileCollection(
                            Usage.JAVA_RUNTIME,
                            creationConfig.mergeSpec,
                            creationConfig.aarOrJarTypeToConsume.jar
                        )
                    )
                    desugarBootclasspath.fromDisallowChanges(creationConfig.bootClasspath)
                }
            }
        }
    }
}
