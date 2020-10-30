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

package com.android.build.api.artifact

import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.BuiltArtifacts
import org.gradle.api.Incubating
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue
import java.io.File
import java.io.Serializable
import java.util.function.Supplier

/**
 * When a [Directory] contains more than one artifact (consider [ArtifactType.APK] with multiple
 * APKs for each density for example), this object will abstract having to deal with
 * [BuiltArtifacts] and manually load and write the metadata files.
 *
 * Instead, users can focus on writing transformation code that transform one file at at time into
 * a newer version. It will also provide the ability to use Gradle's [WorkQueue] to multi-thread
 * the transformations.
 *
 * Here is an example of a [Task] copying (unchanged) the APK files from one location to another.
 *
 * <pre>
 * // parameter interface to pass to work items.
 * interface WorkItemParameters: WorkParameters, Serializable {
 *    val inputApkFile: RegularFileProperty
 *    val outputApkFile: RegularFileProperty
 * }
 * </pre>
 *
 *
 * <pre>
 * // work item that copy one file at a time.
 * abstract class WorkItem @Inject constructor(
 *      private val workItemParameters: WorkItemParameters
 * ): WorkAction<WorkItemParameters> {
 *
 *    override fun execute() {
 *      workItemParameters.inputApkFile.asFile.get().copyTo(
 *      workItemParameters.outputApkFile.get().asFile)
 *    }
 * }
 * </pre>
 *
 * And the task that wires things together :
 *
 * <pre>
 * abstract class CopyApksTask @Inject constructor(private val workers: WorkerExecutor): DefaultTask() {
 *
 * @get:InputFiles
 * abstract val apkFolder: DirectoryProperty
 *
 * @get:OutputDirectory
 * abstract val outFolder: DirectoryProperty
 *
 * @get:Internal
 * abstract val transformationRequest: Property<ArtifactTransformationRequest<CopyApksTask>>
 *
 * @TaskAction
 * fun taskAction() {
 *  transformationRequest.get().submit(
 *     this,
 *     workers.noIsolation(),
 *     WorkItem::class.java,
 *     WorkItemParameters::class.java) {
 *     builtArtifact: BuiltArtifact,
 *     outputLocation: Directory,
 *     param: WorkItemParameters ->
 *       val inputFile = File(builtArtifact.outputFile)
 *       param.inputApkFile.set(inputFile)
 *       param.outputApkFile.set(File(outputLocation.asFile, inputFile.name))
 *       param.outputApkFile.get().asFile
 *     }
 *   }
 * }
 * </pre>]
 */
@Incubating
interface ArtifactTransformationRequest<TaskT: Task> {

    /**
     * Submit a `org.gradle.workers` style of [WorkAction] to process each input [BuiltArtifact]
     *
     * @param task : the Task initiating the [WorkQueue] requests.
     * @param workQueue the Gradle [WorkQueue] instance to use to spawn worker items with.
     * @param actionType the type of the [WorkAction] subclass that process that input [BuiltArtifact]
     * @param parameterType the type of parameters expected by the [WorkAction]
     * @param parameterConfigurator the lambda to configure instances of [parameterType] for each
     * [BuiltArtifact]
     */
    fun <ParamT> submit(
        task: TaskT,
        workQueue: WorkQueue,
        actionType: Class<out WorkAction<ParamT>>,
        parameterType: Class<out ParamT>,
        parameterConfigurator: (
            builtArtifact: BuiltArtifact,
            outputLocation: Directory,
            parameters: ParamT) -> File
    ): Supplier<BuiltArtifacts>
            where ParamT : WorkParameters, ParamT: Serializable

    /**
     * Submit a lambda to process synchronously each input [BuiltArtifact]
     */
    fun submit(task: TaskT, transformer: (input: BuiltArtifact) -> File)
}