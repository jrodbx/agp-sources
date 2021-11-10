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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.manifmerger.XmlDocument
import com.android.utils.PositionXmlParser
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.Serializable
import javax.inject.Inject

@CacheableTask
abstract class ProcessManifestForMetadataFeatureTask : NonIncrementalTask() {

    @get:OutputFile
    abstract val metadataFeatureManifest: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val bundleManifest: RegularFileProperty

    @get:Input
    abstract val dynamicFeature: Property<Boolean>

    @TaskAction
    override fun doTaskAction() {

        val inputFile = bundleManifest.get().asFile
        val metadataFeatureManifestFile = metadataFeatureManifest.get().asFile
        // if there is no feature name to write, just use the original merged manifest file.
        if (!dynamicFeature.get()) {
            inputFile.copyTo(target = metadataFeatureManifestFile, overwrite = true)
            return
        }

        workerExecutor.noIsolation().submit(WorkItem::class.java) {
            it.inputXmlFile.set(bundleManifest)
            it.outputXmlFile.set(metadataFeatureManifestFile)
        }
    }

    interface WorkItemParameters: WorkParameters, Serializable {
        val inputXmlFile: RegularFileProperty
        val outputXmlFile: RegularFileProperty
    }

    abstract class WorkItem@Inject constructor(private val workItemParameters: WorkItemParameters)
        : WorkAction<WorkItemParameters> {
        override fun execute() {
            val xmlDocument = BufferedInputStream(
                FileInputStream(
                    workItemParameters.inputXmlFile.get().asFile
                )
            ).use {
                PositionXmlParser.parse(it)
            }
            stripMinSdkFromFeatureManifest(xmlDocument)
            stripUsesSplitFromFeatureManifest(xmlDocument)
            workItemParameters.outputXmlFile.get().asFile.writeText(
                XmlDocument.prettyPrint(xmlDocument))
        }
    }

    class CreationAction(creationConfig: VariantCreationConfig) :
        VariantTaskCreationAction<ProcessManifestForMetadataFeatureTask, VariantCreationConfig>(
            creationConfig = creationConfig
        ) {
        override val name: String
            get() = computeTaskName("processManifest", "ForFeature")
        override val type: Class<ProcessManifestForMetadataFeatureTask>
            get() = ProcessManifestForMetadataFeatureTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<ProcessManifestForMetadataFeatureTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                    ProcessManifestForMetadataFeatureTask::metadataFeatureManifest
            )
                .withName(SdkConstants.ANDROID_MANIFEST_XML)
                .on(InternalArtifactType.METADATA_FEATURE_MANIFEST)
        }

        override fun configure(task: ProcessManifestForMetadataFeatureTask) {
            super.configure(task)
            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.BUNDLE_MANIFEST,
                task.bundleManifest
            )
            task.dynamicFeature.set(creationConfig.variantType.isDynamicFeature)
        }
    }
}