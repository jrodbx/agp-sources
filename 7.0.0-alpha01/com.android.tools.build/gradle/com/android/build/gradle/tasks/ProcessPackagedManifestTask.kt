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
import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.impl.dirName
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.workeractions.DecoratedWorkParameters
import com.android.build.gradle.internal.workeractions.WorkActionAdapter
import com.android.manifmerger.XmlDocument
import com.android.utils.FileUtils
import com.android.utils.PositionXmlParser
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkerExecutor
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

@CacheableTask
abstract class ProcessPackagedManifestTask @Inject constructor(
    objects: ObjectFactory,
    workers: WorkerExecutor
): NonIncrementalTask() {

    @get:OutputDirectory
    abstract val packageManifests: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedManifests: DirectoryProperty

    @get:Internal
    abstract val transformationRequest: Property<ArtifactTransformationRequest<ProcessPackagedManifestTask>>

    // Use a property to hold the [WorkerExecutor] so unit tests can reset it if necessary.
    @get:Internal
    val workersProperty: Property<WorkerExecutor> = objects.property(WorkerExecutor::class.java)

    init {
        workersProperty.set(workers)
    }

    @TaskAction
    override fun doTaskAction() {

        transformationRequest.get().submit(this,
            workersProperty.get().noIsolation(),
            WorkItem::class.java)
            { builtArtifact: BuiltArtifact, directory: Directory, parameters: WorkItemParameters ->
                parameters.inputXmlFile.set(File(builtArtifact.outputFile))
                parameters.outputXmlFile.set(
                    File(directory.asFile,
                        FileUtils.join(
                            builtArtifact.dirName(),
                            SdkConstants.ANDROID_MANIFEST_XML)))
                parameters.outputXmlFile.get().asFile
            }
    }

    interface WorkItemParameters: DecoratedWorkParameters {
        val inputXmlFile: RegularFileProperty
        val outputXmlFile: RegularFileProperty
    }

    abstract class WorkItem@Inject constructor(private val workItemParameters: WorkItemParameters)
        : WorkActionAdapter<WorkItemParameters> {
        override fun getParameters(): WorkItemParameters = workItemParameters

        override fun doExecute() {

            val xmlDocument = BufferedInputStream(
                FileInputStream(
                workItemParameters.inputXmlFile.get().asFile)
            ).use {
                PositionXmlParser.parse(it)
            }
            removeSplitNames(document = xmlDocument)
            val outputFile = workItemParameters.outputXmlFile.get().asFile
            outputFile.parentFile.mkdirs()
            outputFile.writeText(
                XmlDocument.prettyPrint(xmlDocument))
        }
    }

    class CreationAction(creationConfig: ApkCreationConfig) :
        VariantTaskCreationAction<ProcessPackagedManifestTask, ApkCreationConfig>(
            creationConfig = creationConfig
        ) {
        override val name: String
            get() = computeTaskName("process", "ManifestForPackage")
        override val type: Class<ProcessPackagedManifestTask>
            get() = ProcessPackagedManifestTask::class.java

        private lateinit var transformationRequest: ArtifactTransformationRequest<ProcessPackagedManifestTask>

        override fun handleProvider(taskProvider: TaskProvider<ProcessPackagedManifestTask>) {
            super.handleProvider(taskProvider)
            transformationRequest = creationConfig.artifacts.use(taskProvider)
                .wiredWithDirectories(
                    ProcessPackagedManifestTask::mergedManifests,
                    ProcessPackagedManifestTask::packageManifests)
                .toTransformMany(
                    InternalArtifactType.MERGED_MANIFESTS,
                    InternalArtifactType.PACKAGED_MANIFESTS
                )
        }

        override fun configure(task: ProcessPackagedManifestTask) {
            super.configure(task)
            task.workersProperty.disallowChanges()
            task.transformationRequest.setDisallowChanges(transformationRequest)
        }
    }
}