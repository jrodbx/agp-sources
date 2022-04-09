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
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.services.SymbolTableBuildService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.configureVariantProperties
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.symbols.exportToCompiledJava
import com.android.ide.common.symbols.SymbolIo
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider

@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ANDROID_RESOURCES)
abstract class PrivacySandboxSdkGenerateRClassTask : NonIncrementalTask() {

    @get:OutputFile
    abstract val rClassJar: RegularFileProperty

    @get:Input
    abstract val applicationId: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val symbolListWithPackageNames: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val runtimeSymbolList: RegularFileProperty

    @get:Internal
    abstract val symbolTableBuildService: Property<SymbolTableBuildService>

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(WorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.applicationId.set(applicationId)
            it.rPackages.from(symbolListWithPackageNames)
            it.runtimeSymbolList.set(runtimeSymbolList)
            it.symbolTableBuildService.set(symbolTableBuildService)
            it.rClassJar.set(rClassJar)
        }
    }

    abstract class Params : ProfileAwareWorkAction.Parameters() {

        abstract val applicationId: Property<String>
        abstract val rPackages: ConfigurableFileCollection
        abstract val runtimeSymbolList: RegularFileProperty
        abstract val symbolTableBuildService: Property<SymbolTableBuildService>
        abstract val rClassJar: RegularFileProperty
    }

    abstract class WorkAction : ProfileAwareWorkAction<Params>() {

        override fun run() {
            with(parameters) {
                val applicationId = parameters.applicationId.get()
                val rClassJar = parameters.rClassJar.get().asFile
                val runtimeSymbolList = runtimeSymbolList.get().asFile
                val symbolTableBuildService = symbolTableBuildService.get()
                val assignedValues = SymbolIo.readFromAapt(runtimeSymbolList, null)
                val depSymbolTables = symbolTableBuildService.loadClasspath(rPackages).map {
                    it.withValuesFrom(assignedValues)
                }
                exportToCompiledJava(
                        tables = depSymbolTables,
                        outJar = rClassJar.toPath(),
                        finalIds = false,
                        rPackage = applicationId,
                )
            }
        }
    }

    class CreationAction constructor(
            private val creationConfig: PrivacySandboxSdkVariantScope,
    ) : TaskCreationAction<PrivacySandboxSdkGenerateRClassTask>() {

        override val name = "generateRClass"
        override val type = PrivacySandboxSdkGenerateRClassTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<PrivacySandboxSdkGenerateRClassTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts
                    .setInitialProvider(taskProvider,
                            PrivacySandboxSdkGenerateRClassTask::rClassJar)
                    .on(PrivacySandboxSdkInternalArtifactType.RUNTIME_R_CLASS)
        }

        override fun configure(task: PrivacySandboxSdkGenerateRClassTask) {
            task.configureVariantProperties("", task.project.gradle.sharedServices)
            task.applicationId.setDisallowChanges(creationConfig.bundle.applicationId)
            creationConfig.artifacts.setTaskInputToFinalProduct(
                    PrivacySandboxSdkInternalArtifactType.RUNTIME_SYMBOL_LIST,
                    task.runtimeSymbolList
            )
            task.symbolListWithPackageNames.from(
                    creationConfig.dependencies.getArtifactFileCollection(
                            Usage.JAVA_RUNTIME,
                            creationConfig.mergeSpec,
                            AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME)
            )
            task.symbolTableBuildService.set(getBuildService(creationConfig.services.buildServiceRegistry))
        }
    }
}
