/*
 * Copyright (C) 2019 The Android Open Source Project
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

import org.gradle.api.Incubating
import org.gradle.api.Task
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

/**
 * Operations on a Variant object.
 *
 * This interface is not implemented so far by the Android Gradle Plugin, it's work in
 * progress.
 */
@Incubating
interface Operations {

    /**
     * Get the [Provider] of [FILE_TYPE] for the passed [ArtifactType].
     *
     * The [ArtifactType] must be of the [FILE_TYPE] and [ArtifactType.Single]
     */
    fun <FILE_TYPE: FileSystemLocation, ARTIFACT_TYPE> get(type: ARTIFACT_TYPE): Provider<FILE_TYPE>
            where ARTIFACT_TYPE: ArtifactType<FILE_TYPE>, ARTIFACT_TYPE: ArtifactType.Single

    /**
     * Get all the [Provider] of [FILE_TYPE] for the passed [ArtifactType].
     *
     * The [ArtifactType] must be [ArtifactType.Multiple]
     */
    fun <FILE_TYPE: FileSystemLocation, ARTIFACT_TYPE> getAll(type: ARTIFACT_TYPE): Provider<List<FILE_TYPE>>
            where ARTIFACT_TYPE : ArtifactType<FILE_TYPE>, ARTIFACT_TYPE : ArtifactType.Multiple

    /**
     * Initiates an append request to a [ArtifactType.Multiple] artifact type.
     *
     * @param taskProvider the [TaskProvider] for the task producing an instance of [FILE_TYPE]
     * @param with the method reference to get the [FileSystemLocationProperty] to retrieve the
     * produced [FILE_TYPE] when needed.
     *
     * The artifact type must be [ArtifactType.Multiple] and [ArtifactType.Appendable]
     *
     * Let's take a [Task] with a [org.gradle.api.file.RegularFile] output :
     * <pre>
     *     abstract class MyTask: DefaultTask() {
     *          @get:OutputFile abstract val outputFile: RegularFileProperty
     *
     *          @TaskAction fun taskAction() {
     *              ... write outputFile ...
     *          }
     *     }
     * </pre>
     *
     * and an ArtifactType defined as follow :
     *
     * <pre
     *     sealed class PublicArtifactType<T: FileSystemLocation>(val kind: ArtifactKind) {
     *          object MULTIPLE_FILE_ARTIFACT:
     *                  PublicArtifactType<RegularFile>(FILE), Appendable
     *     }
     * </pre>
     *
     * you can then register a task as a Provider of [org.gradle.api.file.RegularFile] for the
     * artifact type.
     *
     * <pre>
     *     val taskProvider= projects.tasks.register(MyTask::class.java, "appendTask")
     *     Operations.append(taskProvider, MyTask::outputFile)
     *              .on(PublicArtifactType.MULTIPLE_FILE_ARTIFACT)
     * </pre>
     *
     * @return an [AppendRequest] to finish the append request.
     */
    fun <TASK: Task, FILE_TYPE: FileSystemLocation> append(
        taskProvider: TaskProvider<TASK>,
        with: (TASK)-> FileSystemLocationProperty<FILE_TYPE>
    ): AppendRequest<FILE_TYPE>

    /**
     * Initiates a transform request using a [TaskProvider] instance with method references to
     * set the input and the output of transformation.
     * @param taskProvider the [TaskProvider] for the task transforming an instance of [FILE_TYPE]
     * @param from the method reference to get a [Property] to set the transform input
     * @param into the method reference to get the [Provider] to retrieve the produced [FILE_TYPE]
     * when needed.
     *
     * The artifact type must be [ArtifactType.Single] and [ArtifactType.Transformable]
     *
     * Let's take a [Task] with a [org.gradle.api.file.RegularFile] to transform an input into an
     * output :
     * <pre>
     *     abstract class MyTask: DefaultTask() {
     *          @get:InputFile abstract val inputFile: RegularFileProperty
     *          @get:OutputFile abstract val outputFile: RegularFileProperty
     *
     *          @TaskAction fun taskAction() {
     *              ... read inputFile and write outputFile ...
     *          }
     *     }
     * </pre>
     *
     * and an ArtifactType defined as follow :
     *
     * <pre
     *     sealed class PublicArtifactType<T: FileSystemLocation>(val kind: ArtifactKind) {
     *          object SINGLE_FILE_ARTIFACT:
     *                  PublicArtifactType<RegularFile>(FILE), Single, Transformable
     *     }
     * </pre>
     *
     * you can register a transform to the collection of [org.gradle.api.file.RegularFile]
     *
     * <pre>
     *     val taskProvider= projects.tasks.register(MyTask::class.java, "transformTask")
     *     Operations.transform(taskProvider, MyTask::inputFile, MyTask::outputFile)
     *              .on(PublicArtifactType.SINGLE_FILE_ARTIFACT)
     * </pre>
     *
     * @return a instance of [TransformRequest] that can be used to specify the artifact type.
     */
    fun <TASK: Task, FILE_TYPE: FileSystemLocation> transform(
        taskProvider: TaskProvider<TASK>,
        from: (TASK)-> FileSystemLocationProperty<FILE_TYPE>,
        into: (TASK) -> FileSystemLocationProperty<FILE_TYPE>
    ): TransformRequest<FILE_TYPE>

    /**
     * Initiates a transform request.
     *
     * @param taskProvider the [TaskProvider] for the task transforming an instance of [FILE_TYPE]
     * @param from the method reference to get a [ListProperty] to set all the transform inputs
     * @param into the method reference to get the [Provider] to retrieve the produced [FILE_TYPE]
     * when needed.
     *
     * The artifact type must be [ArtifactType.Multiple] and [ArtifactType.Transformable]
     *
     * The implementation of the task must combine all the inputs returned [from] the method
     * reference and store [into] a single output.
     * Chained transforms will get a list of a single output from the upstream transform.
     *
     * If some [append] calls are made on the same artifact type, the first transform will always
     * get the complete list of artifacts irrespective of the timing of the calls.
     *
     * Let's take a [Task] to transform a list of [org.gradle.api.file.RegularFile] as inputs into
     * a single output :
     * <pre>
     *     abstract class MyTask: DefaultTask() {
     *          @get:InputFiles abstract val inputFiles: ListProperty<RegularFile>
     *          @get:OutputFile abstract val outputFile: RegularFileProperty
     *
     *          @TaskAction fun taskAction() {
     *              ... read all inputFiles and write outputFile ...
     *          }
     *     }
     * </pre>
     *
     * and an ArtifactType defined as follow :
     *
     * <pre
     *     sealed class PublicArtifactType<T: FileSystemLocation>(val kind: ArtifactKind) {
     *          object MULTIPLE_FILE_ARTIFACT:
     *                  PublicArtifactType<RegularFile>(FILE), Multiple, Replaceable
     *     }
     * </pre>
     *
     * you can register a transform to the collection of [org.gradle.api.file.RegularFile]
     *
     * <pre>
     *     val taskProvider= projects.tasks.register(MyTask::class.java, "combineTask")
     *     Operations.transformAll(taskProvider, MyTask::inputFiles, MyTask::outputFile)
     *              .on(PublicArtifactType.MULTIPLE_FILE_ARTIFACT)
     * </pre>
     *
     * @return a instance of [TransformRequest] that can be used to specify the artifact type.
     */
    fun <TASK: Task, FILE_TYPE: FileSystemLocation> transformAll(
        taskProvider: TaskProvider<TASK>,
        from: (TASK)-> ListProperty<FILE_TYPE>,
        into: (TASK) -> FileSystemLocationProperty<FILE_TYPE>
    ): MultipleTransformRequest<FILE_TYPE>

    /**
     * Initiates a replacement request
     *
     * @param taskProvider a [TaskProvider] for the task producing an instance of [FILE_TYPE]
     * @param with the method reference to obtain the [Provider] for the produced [FILE_TYPE]
     *
     * The artifact type must be [ArtifactType.Replaceable]
     *
     * A replacement request does not care about the existing producer as it replaces it. Therefore
     * the existing producer will not execute.
     * Please note that when such replace requests are made, the TASK will replace initial AGP
     * providers.
     *
     * You cannot replace [ArtifactType.Multiple] artifact type, therefore you must instead combine
     * it using the [Operations.transformAll] API.
     *
     * Let's take a [Task] with a [org.gradle.api.file.RegularFile] output :
     *
     * <pre>
     *     abstract class MyTask: DefaultTask() {
     *          @get:OutputFile abstract val outputFile: RegularFileProperty
     *
     *          @TaskAction fun taskAction() {
     *              ... write outputFile ...
     *          }
     *     }
     * </pre>
     *
     * and an ArtifactType defined as follow :
     *
     * <pre
     *     sealed class PublicArtifactType<T: FileSystemLocation>(val kind: ArtifactKind) {
     *          object SINGLE_FILE_ARTIFACT:
     *                  PublicArtifactType<RegularFile>(FILE), Replaceable
     *     }
     * </pre>
     *
     * you can register a transform to the collection of [org.gradle.api.file.RegularFile]
     *
     * <pre>
     *     val taskProvider= projects.tasks.register(MyTask::class.java, "replaceTask")
     *     Operations.replace(taskProvider, MyTask::outputFile)
     *              .on(PublicArtifactType.SINGLE_FILE_ARTIFACT)
     * </pre>
     */
    fun <TASK: Task, FILE_TYPE: FileSystemLocation> replace(
        taskProvider: TaskProvider<TASK>,
        with: (TASK)-> FileSystemLocationProperty<FILE_TYPE>
    ): ReplaceRequest<FILE_TYPE>
}

/**
 * A transform request on a single [FILE_TYPE] abstraction.
 */
@Incubating
interface TransformRequest<FILE_TYPE: FileSystemLocation> {
    /**
     * Specifies the artifact type this single file transform request applies to.
     * @param type the artifact type which must be of the right [FILE_TYPE], but also
     * [ArtifactType.Transformable] and [ArtifactType.Single]
     */
    fun <ARTIFACT_TYPE> on(type: ARTIFACT_TYPE)
            where ARTIFACT_TYPE: ArtifactType<FILE_TYPE>,
                  ARTIFACT_TYPE: ArtifactType.Transformable,
                  ARTIFACT_TYPE: ArtifactType.Single
}

/**
 * A transform request on a multiple [FILE_TYPE] abstraction.
 */
@Incubating
interface MultipleTransformRequest<FILE_TYPE: FileSystemLocation> {
    /**
     * Specifies the artifact type this multiple file transform request applies to.
     * @param type the artifact type which must be of the right [FILE_TYPE], but also
     * [ArtifactType.Transformable] and [ArtifactType.Multiple]
     */
    fun <ARTIFACT_TYPE> on(type: ARTIFACT_TYPE)
            where ARTIFACT_TYPE: ArtifactType<FILE_TYPE>,
                  ARTIFACT_TYPE: ArtifactType.Transformable,
                  ARTIFACT_TYPE: ArtifactType.Multiple
}

/**
 * A replace request on a single or multiple [FILE_TYPE] abstraction.
 */
@Incubating
interface ReplaceRequest<FILE_TYPE: FileSystemLocation> {
    /**
     * Specifies the artifact type this multiple file replace request applies to.
     * @param type the artifact type which must be of the right [FILE_TYPE], but also
     * [ArtifactType.Replaceable].
     */
    fun <ARTIFACT_TYPE> on(type: ARTIFACT_TYPE)
            where ARTIFACT_TYPE: ArtifactType<FILE_TYPE>,
                  ARTIFACT_TYPE: ArtifactType.Replaceable
}

/**
 * An append request on a multiple [FILE_TYPE] abstraction.
 */
@Incubating
interface AppendRequest<FILE_TYPE: FileSystemLocation> {
    /**
     * Specifies the artifact type this multiple file append request applies to.
     * @param type the artifact type which must be of the right [FILE_TYPE], but also
     * [ArtifactType.Appendable] and [ArtifactType.Multiple]
     */
    fun <ARTIFACT_TYPE> on(type: ARTIFACT_TYPE): AppendRequest<FILE_TYPE>
            where ARTIFACT_TYPE: ArtifactType<FILE_TYPE>,
                  ARTIFACT_TYPE: ArtifactType.Appendable
}