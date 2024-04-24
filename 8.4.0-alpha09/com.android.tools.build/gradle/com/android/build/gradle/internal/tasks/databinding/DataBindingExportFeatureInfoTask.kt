/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.databinding

import android.databinding.tool.DataBindingBuilder
import android.databinding.tool.FeaturePackageInfo
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import com.android.utils.FileUtils
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * This task collects necessary information for the data binding annotation processor to generate
 * the correct code.
 * <p>
 * It has 2 main functionality:
 * a) copy the package id resource offset for the feature so that data binding can properly offset
 * BR class ids.
 *
 * b) copy the BR-bin files from dependencies FOR WHICH a BR file needs to be generated.
 * These are basically dependencies which need to be packaged by this feature. (e.g. if a library
 * dependency is already a dependency of another feature, its BR class will already have been
 * generated)
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.DATA_BINDING)
abstract class DataBindingExportFeatureInfoTask : NonIncrementalTask() {

    @get:OutputDirectory abstract val outFolder: DirectoryProperty

    @get:Input
    abstract val resOffset: Property<Int>

    /**
     * In a feature, we only need to generate code for its Runtime dependencies as compile
     * dependencies are already available via other dependencies (base feature or another feature)
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var directDependencies: FileCollection
        private set

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(ExportFeatureInfoRunnable::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.outFolder.set(outFolder)
            it.resOffset.set(resOffset)
            it.directDependencies.set(directDependencies.asFileTree.files)
        }
    }

    class CreationAction(
        componentProperties: ApkCreationConfig
    ) : VariantTaskCreationAction<DataBindingExportFeatureInfoTask, ApkCreationConfig>(
        componentProperties
    ) {

        override val name: String
            get() = computeTaskName("dataBindingExportFeatureInfo")
        override val type: Class<DataBindingExportFeatureInfoTask>
            get() = DataBindingExportFeatureInfoTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<DataBindingExportFeatureInfoTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                DataBindingExportFeatureInfoTask::outFolder
            ).on(InternalArtifactType.FEATURE_DATA_BINDING_FEATURE_INFO)
        }

        override fun configure(
            task: DataBindingExportFeatureInfoTask
        ) {
            super.configure(task)

            task.directDependencies = creationConfig.variantDependencies.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.DATA_BINDING_ARTIFACT)
            check(creationConfig is DynamicFeatureCreationConfig) {
                "Unexpected type: ${creationConfig.javaClass.name}"
            }
            task.resOffset.set(creationConfig.resOffset)
            task.resOffset.disallowChanges()
        }
    }
}

abstract class ExportFeatureInfoParams: ProfileAwareWorkAction.Parameters() {
    abstract val outFolder: DirectoryProperty
    abstract val resOffset: Property<Int>
    abstract val directDependencies: SetProperty<File>
}

abstract class ExportFeatureInfoRunnable: ProfileAwareWorkAction<ExportFeatureInfoParams>() {
    override fun run() {
        val outputFolder = parameters.outFolder.get().asFile
        FileUtils.cleanOutputDir(outputFolder)
        outputFolder.mkdirs()
        parameters.directDependencies.get().filter {
            it.name.endsWith(DataBindingBuilder.BR_FILE_EXT)
        }.forEach {
            FileUtils.copyFile(it, File(outputFolder, it.name))
        }
        // save the package id offset
        FeaturePackageInfo(packageId = parameters.resOffset.get()).serialize(
                File(outputFolder, DataBindingBuilder.FEATURE_BR_OFFSET_FILE_NAME)
        )
    }
}
