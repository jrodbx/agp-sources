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
import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.internal.workeractions.WorkActionAdapter
import com.android.ide.common.workers.GradlePluginMBeans
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue
import java.io.File
import java.io.Serializable
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

class ArtifactTransformationRequestImpl<ArtifactTypeT, TaskT: Task>(
    private val builtArtifactsReference: AtomicReference<BuiltArtifactsImpl>,
    private val inputLocation: (TaskT) -> FileSystemLocationProperty<Directory>,
    private val outputLocation: (TaskT) -> FileSystemLocationProperty<Directory>,
    private val outputArtifactType: ArtifactTypeT
) : Serializable, ArtifactTransformationRequest<TaskT>
        where ArtifactTypeT: Artifact.SingleArtifact<Directory> {

    override fun <ParamT> submit(
        task: TaskT,
        workQueue: WorkQueue,
        actionType: Class<out WorkAction<ParamT>>,
        parameterType : Class<out ParamT>,
        parameterConfigurator: (builtArtifact: BuiltArtifact, outputLocation: Directory, parameters: ParamT) -> File): Supplier<BuiltArtifacts>
            where ParamT : WorkParameters, ParamT: Serializable {

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
            workQueue.submit(actionType) {
                mapOfBuiltArtifactsToParameters[builtArtifact] =
                    parameterConfigurator(
                        builtArtifact,
                        outputLocation(task).get(),
                        it)
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

    /**
     * AGP Private API similar to [submit] that will include all profiling information related to
     * workers.
     *
     * @param workQueue the Gradle [WorkQueue] instance to use to spawn worker items with.
     * @param actionType the type of the [WorkAction] subclass that process that input [BuiltArtifact]
     * @param parameterType the type of parameters expected by the [WorkAction]
     * @param parameterConfigurator the lambda to configure instances of [parameterType] for each
     * [BuiltArtifact]
     */
    fun <ParamT> submitWithProfiler(
        task: TaskT,
        objects: ObjectFactory,
        workQueue: WorkQueue,
        actionType: Class<out WorkAction<ParamT>>,
        parameterType : Class<out ParamT>,
        parameterConfigurator: (
            builtArtifact: BuiltArtifactImpl,
            outputLocation: Directory,
            parameters: ParamT) -> File
    ): Supplier<BuiltArtifactsImpl> where ParamT : WorkParameters, ParamT: Serializable {

        if (actionType.classLoader != WorkActionAdapter::class.java.classLoader) {
            throw RuntimeException("""${actionType.name} class was loaded in a different
classloader from the AGP plugin, therefore you must use the submitWithProfiler
method with a concrete WorkActionAdapter implementation.""")
        }
        @Suppress("UNCHECKED_CAST")
        return submitWithProfiler(
            task = task,
            objects = objects,
            workQueue = workQueue,
            concreteAction = WorkActionAdapter::class.java
                    as Class<WorkActionAdapter<ParamT, WorkActionAdapter.AdaptedWorkParameters<ParamT>>>,
            actionType = actionType,
            parameterType = parameterType,
            parameterConfigurator = parameterConfigurator
        )
    }

    /**
     * AGP Private API similar to [submit] that will include all profiling information related to
     * workers.
     *
     * This version should be used when [actionType] or [parameterType] are not loaded in the same
     * classloader as this class. In such a case, a concrete class is required so Gradle can
     * serialize and de-serialize using the concrete class [ClassLoader] rather than this class
     * [ClassLoader]
     *
     * @param workQueue the Gradle [WorkQueue] instance to use to spawn worker items with.
     * @param concreteAction Concrete implementation of [WorkActionAdapter] loaded in the [Task]'s
     * [ClassLoader]
     * @param actionType the type of the [WorkAction] subclass that process that input [BuiltArtifact]
     * @param parameterType the type of parameters expected by the [WorkAction]
     * @param parameterConfigurator the lambda to configure instances of [parameterType] for each
     * [BuiltArtifact]
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun <ParamT, ConcreteParametersT> submitWithProfiler(
        objects: ObjectFactory,
        task: TaskT,
        workQueue: WorkQueue,
        concreteAction: Class<WorkActionAdapter<ParamT, ConcreteParametersT>>,
        actionType: Class<out WorkAction<ParamT>>,
        parameterType : Class<out ParamT>,
        parameterConfigurator: (
            builtArtifact: BuiltArtifactImpl,
            outputLocation: Directory,
            parameters: ParamT) -> File
    ): Supplier<BuiltArtifactsImpl>
            where ParamT : WorkParameters,
                  ParamT: Serializable,
                  ConcreteParametersT: WorkActionAdapter.AdaptedWorkParameters<ParamT>,
                  ConcreteParametersT: Serializable {

        val parametersList = mutableMapOf<BuiltArtifact, File>()
        val sourceBuiltArtifacts =
            BuiltArtifactsLoaderImpl().load(inputLocation(task).get())
                ?: throw RuntimeException("No provided artifacts.")

        sourceBuiltArtifacts.elements.forEach { builtArtifact ->
            workQueue.submit(concreteAction
                    as Class<out WorkActionAdapter<ParamT, ConcreteParametersT>>) {
                    parameters: ConcreteParametersT ->

                if (workQueue is ProfilerEnabledWorkQueue) {
                    // if the AGP extensions are present, record the worker creation and
                    // provide enough context to the WorkerActionAdapter to be able to send the
                    // necessary events.
                    val workerKey = "${workQueue.projectName}${workQueue.taskName}${builtArtifact.hashCode()}"
                    parameters.projectName = workQueue.projectName
                    parameters.tastName = workQueue.taskName
                    parameters.workerKey = workerKey

                    GradlePluginMBeans.getProfileMBean(workQueue.projectName)
                        ?.workerAdded(workQueue.taskName, workerKey)
                }
                // create the real action parameter object
                val targetActionParameters = objects.newInstance(parameterType)
                parameters.adaptedParameters = targetActionParameters

                // get the proposed output file from the configuration lambda.
                val outputFile = parameterConfigurator(
                    builtArtifact,
                    outputLocation(task).get(),
                    targetActionParameters)

                // save the output file which we will use to create the BuiltArtifact instances.
                parametersList[builtArtifact] = outputFile
                parameters.adaptedAction = actionType.name
            }
        }

        builtArtifactsReference.set(
            BuiltArtifactsImpl(
                artifactType = outputArtifactType,
                applicationId = sourceBuiltArtifacts.applicationId,
                variantName = sourceBuiltArtifacts.variantName,
                elements = sourceBuiltArtifacts.elements.map { builtArtifact ->
                    builtArtifact.newOutput(parametersList[builtArtifact]?.toPath()
                        ?: throw RuntimeException("Cannot find BuiltArtifact")
                    )
                }))

        return Supplier {
            // since the user code wants to have access to the new BuiltArtifacts, await on the
            // WorkQueue so we are sure the output files are all present.
            workQueue.await()
            builtArtifactsReference.get()
        }
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
