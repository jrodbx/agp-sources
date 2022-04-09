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

package com.android.build.api.artifact.impl

import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.BuiltArtifacts
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.internal.tasks.BaseTask
import com.android.build.gradle.internal.workeractions.DecoratedWorkParameters
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue
import java.io.File
import java.io.Serializable
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

class ArtifactTransformationRequestImpl<TaskT: Task>(
    private val builtArtifactsReference: AtomicReference<BuiltArtifactsImpl>,
    private val inputLocation: (TaskT) -> FileSystemLocationProperty<Directory>,
    private val outputLocation: (TaskT) -> FileSystemLocationProperty<Directory>,
    private val outputArtifactType: Artifact.Single<Directory>
) : Serializable, ArtifactTransformationRequest<TaskT> {

    override fun <ParamT: WorkParameters> submit(
        task: TaskT,
        workQueue: WorkQueue,
        actionType: Class<out WorkAction<ParamT>>,
        parameterConfigurator: (builtArtifact: BuiltArtifact, outputLocation: Directory, parameters: ParamT) -> File
    ): Supplier<BuiltArtifacts> {

        val mapOfBuiltArtifactsToParameters = mutableMapOf<BuiltArtifact, File>()
        val sourceBuiltArtifacts =
            BuiltArtifactsLoaderImpl().load(inputLocation(task).get())

        if (sourceBuiltArtifacts == null) {
            builtArtifactsReference.set(
                BuiltArtifactsImpl(
                    artifactType = outputArtifactType,
                    applicationId = "unknown",
                    variantName = "unknown",
                    elements = listOf()))
            return Supplier { builtArtifactsReference.get() }
        }

        sourceBuiltArtifacts.elements.forEach {builtArtifact ->
            workQueue.submit(actionType) {parameters ->

                if (task is BaseTask && parameters is DecoratedWorkParameters) {
                    // Record the worker creation and provide enough context to the WorkerActionAdapter
                    // to be able to send the necessary events.
                    val workerKey = "${task.path}${builtArtifact.hashCode()}"
                    parameters.taskPath.set(task.path)
                    parameters.workerKey.set(workerKey)
                    parameters.analyticsService.set(task.analyticsService)

                    parameters.analyticsService.get()
                        .workerAdded(task.path, workerKey)
                }

                mapOfBuiltArtifactsToParameters[builtArtifact] =
                    parameterConfigurator(
                        builtArtifact,
                        outputLocation(task).get(),
                        parameters)
            }
        }

        builtArtifactsReference.set(
            BuiltArtifactsImpl(
                artifactType = outputArtifactType,
                applicationId = sourceBuiltArtifacts.applicationId,
                variantName = sourceBuiltArtifacts.variantName,
                elements = sourceBuiltArtifacts.elements
                    .map {
                        val output = mapOfBuiltArtifactsToParameters[it]
                            ?: throw RuntimeException("Unknown BuiltArtifact $it, file a bug")
                        it.newOutput(output.toPath())
                    }
            )
        )
        return Supplier {
            // since the user code wants to have access to the new BuiltArtifacts, await on the
            // WorkQueue so we are sure the output files are all present.
            workQueue.await()
            builtArtifactsReference.get()
        }
    }

    internal fun wrapUp(task: TaskT) {
        // save the metadata file in the output location upon completion of all the workers.
        builtArtifactsReference.get().save(outputLocation(task).get())
    }

    override fun submit(
        task: TaskT,
        transformer: (input: BuiltArtifact) -> File) {

        val sourceBuiltArtifacts = BuiltArtifactsLoaderImpl().load(inputLocation(task).get())
            ?: throw RuntimeException("No provided artifacts.")

        builtArtifactsReference.set(BuiltArtifactsImpl(
            applicationId = sourceBuiltArtifacts.applicationId,
            variantName = sourceBuiltArtifacts.variantName,
            artifactType = outputArtifactType,
            elements = sourceBuiltArtifacts.elements.map {
                it.newOutput(transformer(it).toPath())
            }))
    }
}
