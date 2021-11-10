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
import android.databinding.tool.store.FeatureInfoList
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclaration
import com.android.utils.FileUtils
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.tooling.BuildException
import java.io.File
import java.io.FileNotFoundException

/**
 * This task collects the feature information and exports their namespaces into a file which can be
 * read by the DataBindingAnnotationProcessor.
 */
@CacheableTask
abstract class DataBindingExportFeatureNamespacesTask : NonIncrementalTask() {
    // where to keep the log of the task
    @get:OutputDirectory abstract val packageListOutFolder: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var featureDeclarations: FileCollection
        private set

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(ExportNamespacesRunnable::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.featureDeclarations.set(featureDeclarations.asFileTree.files)
            it.packageListOutFolder.set(packageListOutFolder.get().asFile)
        }
    }

    class CreationAction(
        creationConfig: VariantCreationConfig
    ) :
        VariantTaskCreationAction<DataBindingExportFeatureNamespacesTask, VariantCreationConfig>(
            creationConfig
        ) {

        override val name: String
            get() = computeTaskName("dataBindingExportFeatureNamespaces")
        override val type: Class<DataBindingExportFeatureNamespacesTask>
            get() = DataBindingExportFeatureNamespacesTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<DataBindingExportFeatureNamespacesTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                DataBindingExportFeatureNamespacesTask::packageListOutFolder
            ).on(InternalArtifactType.FEATURE_DATA_BINDING_BASE_FEATURE_INFO)
        }

        override fun configure(
            task: DataBindingExportFeatureNamespacesTask
        ) {
            super.configure(task)

            task.featureDeclarations = creationConfig.variantDependencies.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    AndroidArtifacts.ArtifactType.REVERSE_METADATA_FEATURE_DECLARATION
            )
        }
    }
}

abstract class ExportNamespacesParams : ProfileAwareWorkAction.Parameters() {
    abstract val featureDeclarations: SetProperty<File>
    abstract val packageListOutFolder: DirectoryProperty
}

abstract class ExportNamespacesRunnable: ProfileAwareWorkAction<ExportNamespacesParams>() {
    override fun run() {
        val packages = mutableSetOf<String>()
        for (featureSplitDeclaration in parameters.featureDeclarations.get()) {
            try {
                val loaded = FeatureSplitDeclaration.load(featureSplitDeclaration)
                packages.add(loaded.namespace)
            } catch (e: FileNotFoundException) {
                throw BuildException("Cannot read features split declaration file", e)
            }
        }
        val outputFolder = parameters.packageListOutFolder.get().asFile
        FileUtils.cleanOutputDir(outputFolder)
        outputFolder.mkdirs()
        // save the list.
        FeatureInfoList(packages).serialize(
                File(
                    outputFolder,
                    DataBindingBuilder.FEATURE_PACKAGE_LIST_FILE_NAME
                )
        )
    }
}
