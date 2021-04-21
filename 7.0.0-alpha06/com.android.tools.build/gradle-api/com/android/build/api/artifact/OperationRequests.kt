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

import org.gradle.api.Incubating
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty

/**
 * Operations performed by a [Task] with a single [RegularFile] or [Directory] output.
 *
 * [Task] is not consuming existing version of the target [ArtifactType].
 */
@Incubating
interface OutOperationRequest<FileTypeT: FileSystemLocation> {
    /**
     * Initiates an append request to a [Artifact.MultipleArtifact] artifact type.
     *
     * @param type The [Artifact] of [FileTypeT] identifying the artifact to append to.
     *
     * The artifact type must be [Artifact.MultipleArtifact] and [Artifact.Appendable].
     *
     * As an example, let's take a [Task] that outputs a [org.gradle.api.file.RegularFile]:
     * ```kotlin
     *     abstract class MyTask: DefaultTask() {
     *          @get:OutputFile abstract val outputFile: RegularFileProperty
     *
     *          @TaskAction fun taskAction() {
     *              ... outputFile.get().asFile.write( ... ) ...
     *          }
     *     }
     * ```
     *
     * and an ArtifactType defined as follows :
     *
     * ```kotlin
     *     sealed class ArtifactType<T: FileSystemLocation>(
     *          val kind: ArtifactKind
     *     ): MultipleArtifactType {
     *          object MULTIPLE_FILE_ARTIFACT:
     *                  ArtifactType<RegularFile>(FILE), Appendable
     *     }
     * ```
     *
     * You can then register the above task as a Provider of [org.gradle.api.file.RegularFile] for
     * that artifact type:
     *
     * ```kotlin
     *     val taskProvider= projects.tasks.register(MyTask::class.java, "appendTask")
     *     artifacts.use(taskProvider)
     *      .wiredWith(MyTask::outputFile)
     *      .toAppendTo(ArtifactType.MULTIPLE_FILE_ARTIFACT)
     * ```
     */
    fun <ArtifactTypeT> toAppendTo(type: ArtifactTypeT)
        where ArtifactTypeT : Artifact.MultipleArtifact<FileTypeT>,
              ArtifactTypeT : Artifact.Appendable

    /**
     * Initiates a creation request for a single [Artifact.Replaceable] artifact type.
     *
     * @param type The [Artifact] of [FileTypeT] identifying the artifact to replace.
     *
     * The artifact type must be [Artifact.Replaceable]
     *
     * A creation request does not care about the existing producer, since it replaces the existing
     * producer. Therefore the existing producer task will not execute (unless it produces other
     * outputs). Please note that when such replace requests are made, the [Task] will replace
     * initial AGP providers.
     *
     * You cannot replace the [Artifact.MultipleArtifact] artifact type; therefore, you must instead
     * combine it using the [TaskBasedOperation.wiredWith] API.
     *
     * For example, let's take a [Task] that outputs a [org.gradle.api.file.RegularFile]:
     *
     * ```kotlin
     *     abstract class MyTask: DefaultTask() {
     *          @get:OutputFile abstract val outputFile: RegularFileProperty
     *
     *          @TaskAction fun taskAction() {
     *              ... write outputFile ...
     *          }
     *     }
     * ```
     *
     * An [ArtifactType] is defined as follows:
     *
     * ```kotlin
     *     sealed class ArtifactType<T: FileSystemLocation>(val kind: ArtifactKind) {
     *          object SINGLE_FILE_ARTIFACT:
     *                  ArtifactType<RegularFile>(FILE), Replaceable
     *     }
     * ```
     *
     * You can register a transform to the collection of [org.gradle.api.file.RegularFile]:
     *
     * ```kotlin
     *     val taskProvider= projects.tasks.register(MyTask::class.java, "replaceTask")
     *     artifacts.use(taskProvider)
     *      .wiredWith(MyTask::outputFile)
     *      .toCreate(ArtifactType.SINGLE_FILE_ARTIFACT)
     * ```
     */
    fun <ArtifactTypeT> toCreate(type: ArtifactTypeT)
        where ArtifactTypeT : Artifact.SingleArtifact<FileTypeT>,
              ArtifactTypeT : Artifact.Replaceable
}

/**
 * Operations performed by a [Task] with a single [RegularFile] or [Directory] output.
 *
 * [Task] is consuming existing version of the target [ArtifactType] and producing a new version.
 */
@Incubating
interface InAndOutFileOperationRequest {
    /**
     * Initiates a transform request to a single [Artifact.Transformable] artifact type.
     *
     * @param type The [Artifact] identifying the artifact to transform. The [Artifact]'s
     * [Artifact.kind] must be [Artifact.FILE].
     *
     * The artifact type must be [Artifact.SingleArtifact] and [Artifact.Transformable].
     *
     * As an example, let's take a [Task] transforming an input [org.gradle.api.file.RegularFile]
     * into an output:
     * ```kotlin
     *     abstract class MyTask: DefaultTask() {
     *          @get:InputFile abstract val inputFile: RegularFileProperty
     *          @get:OutputFile abstract val outputFile: RegularFileProperty
     *
     *          @TaskAction fun taskAction() {
     *              ... read inputFile and write outputFile ...
     *          }
     *     }
     * ```
     *
     * An ArtifactType defined as follows :
     *
     * ```kotlin
     *     sealed class ArtifactType<T: FileSystemLocation>(val kind: ArtifactKind) {
     *          object SINGLE_FILE_ARTIFACT:
     *                  ArtifactType<RegularFile>(FILE), Single, Transformable
     *     }
     * ```
     *
     * You can register a transform to the collection of [org.gradle.api.file.RegularFile].
     *
     * ```kotlin
     *     val taskProvider= projects.tasks.register(MyTask::class.java, "transformTask")
     *     artifacts.use(taskProvider)
     *      .wiredWithFiles(
     *          MyTask::inputFile,
     *          MyTask::outputFile)
     *      .toTransform(ArtifactType.SINGLE_FILE_ARTIFACT)
     * ```
     */
    fun <ArtifactTypeT> toTransform(type: ArtifactTypeT)
        where ArtifactTypeT: Artifact.SingleArtifact<RegularFile>,
              ArtifactTypeT: Artifact.Transformable
}

@Incubating
interface CombiningOperationRequest<FileTypeT: FileSystemLocation> {
    /**
     * Initiates a transform request to a multiple [Artifact.Transformable] artifact type.
     *
     * @param type The [Artifact] of [FileTypeT] identifying the artifact to transform.
     *
     * The artifact type must be [Artifact.MultipleArtifact] and [Artifact.Transformable].
     *
     * The implementation of the task must combine all the inputs into a single output.
     * Chained transforms will get a [ListProperty] containing the single output from the upstream
     * transform.
     *
     * If some [append] calls are made on the same artifact type, the first transform will always
     * get the complete list of artifacts irrespective of the timing of the calls.
     *
     * In the following example, let's take a [Task] to transform a list of
     * [org.gradle.api.file.RegularFile] as inputs into a single output:
     * ```kotlin
     *     abstract class MyTask: DefaultTask() {
     *          @get:InputFiles abstract val inputFiles: ListProperty<RegularFile>
     *          @get:OutputFile abstract val outputFile: RegularFileProperty
     *
     *          @TaskAction fun taskAction() {
     *              ... read all inputFiles and write outputFile ...
     *          }
     *     }
     * ```
     *
     * An [ArtifactType] defined as follows :
     *
     * ```kotlin
     *     sealed class ArtifactType<T: FileSystemLocation>(val kind: ArtifactKind) {
     *          object MULTIPLE_FILE_ARTIFACT:
     *                  ArtifactType<RegularFile>(FILE), Multiple, Transformable
     *     }
     * ```
     *
     * You then register the task as follows:
     *
     * ```kotlin
     *     val taskProvider= projects.tasks.register(MyTask::class.java, "combineTask")
     *     artifacts.use(taskProvider)
     *      .wiredWith(
     *          MyTask::inputFiles,
     *          MyTask::outputFile)
     *      .toTransform(ArtifactType.MULTIPLE_FILE_ARTIFACT)
     * ```
     */
    fun <ArtifactTypeT> toTransform(type: ArtifactTypeT)
        where ArtifactTypeT: Artifact.MultipleArtifact<FileTypeT>,
              ArtifactTypeT: Artifact.Transformable
}

@Incubating
interface InAndOutDirectoryOperationRequest<TaskT : Task> {

    /**
     * Initiates a transform request to a single [Artifact.Transformable] artifact type.
     *
     * @param type The [Artifact] identifying the artifact to transform. The [Artifact]'s
     * [Artifact.kind] must be [Artifact.DIRECTORY]
     *
     * The artifact type must be [Artifact.SingleArtifact] and [Artifact.Transformable].
     *
     * Let's take a [Task] transforming an input [org.gradle.api.file.Directory] into an
     * output:
     * ```kotlin
     *     abstract class MyTask: DefaultTask() {
     *          @get:InputFiles abstract val inputDir: DirectoryProperty
     *          @get:OutputDirectory abstract val outputDir: DirectoryProperty
     *
     *          @TaskAction fun taskAction() {
     *              ... read inputFile and write outputFile ...
     *          }
     *     }
     * ```
     *
     * An ArtifactType defined as follows :
     *
     * ```kotlin
     *     sealed class ArtifactType<T: FileSystemLocation>(val kind: ArtifactKind) {
     *          object SINGLE_DIR_ARTIFACT:
     *                  ArtifactType<Directory>(DIRECTORY), Single, Transformable
     *     }
     * ```
     *
     * You can register a transform to the collection of [org.gradle.api.file.RegularFile].
     *
     * ```kotlin
     *     val taskProvider= projects.tasks.register(MyTask::class.java, "transformTask")
     *     artifacts.use(taskProvider)
     *      .wiredWithDirectories(
     *          MyTask::inputFile,
     *          MyTask::outputFile)
     *      .toTransform(ArtifactType.SINGLE_DIR_ARTIFACT)
     * ```
     */
    fun <ArtifactTypeT> toTransform(type: ArtifactTypeT)
        where ArtifactTypeT: Artifact.SingleArtifact<Directory>,
              ArtifactTypeT: Artifact.Transformable

    /**
     * Initiates a transform request to a single [Artifact.Transformable] artifact type that can
     * contain more than one artifact.
     *
     * @param type The [Artifact] of the [Directory] identifying the artifact to transform.
     * @return [ArtifactTransformationRequest] that will allow processing of individual artifacts
     * located in the input directory.
     *
     * The artifact type must be [Artifact.SingleArtifact], [Artifact.Transformable],
     * and [Artifact.ContainsMany].
     *
     * For example, let's take a [Task] to transform a list of [org.gradle.api.file.RegularFile] as inputs into
     * a single output:
     * ```kotlin
     *     abstract class MyTask: DefaultTask() {
     *          @get:InputFiles abstract val inputFolder: DirectoryProperty
     *          @get:OutputFile abstract val outputFolder: DirectoryProperty
     *          @Internal abstract Property<ArtifactTransformationRequest<MyTask>> getTransformationRequest()
     *
     *          @TaskAction fun taskAction() {
     *             transformationRequest.get().submit(
     *                  ... submit a work item for each input file ...
     *             )
     *          }
     *     }
     * ```
     **
     * You then register the task as follows:
     *
     * ```kotlin
     *     val taskProvider= projects.tasks.register(MyTask::class.java, "combineTask")
     *     val transformationRequest = artifacts.use(taskProvider)
     *       .wiredWith(
     *          MyTask::inputFolder,
     *          MyTask::outputFolder)
     *       .toTransformMany(ArtifactType.APK)
     *     taskProvider.configure { task ->
     *          task.getTransformationRequest().set(transformationRequest)
     *     }
     * ```
     */
    fun <ArtifactTypeT> toTransformMany(type: ArtifactTypeT): ArtifactTransformationRequest<TaskT>
        where ArtifactTypeT: Artifact.SingleArtifact<Directory>,
              ArtifactTypeT: Artifact.ContainsMany
}