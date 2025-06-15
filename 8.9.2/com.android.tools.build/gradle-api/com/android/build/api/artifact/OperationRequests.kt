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
 * [Task] is not consuming existing version of the target [SingleArtifact].
 */
interface OutOperationRequest<FileTypeT: FileSystemLocation> {
    /**
     * Sets the output file or directory name for the new artifact.
     *
     * This name will only be used when [toAppendTo] or [toCreate] is invoked.
     * Although you can use this method on [Directory], it will most likely not influence how
     * the files contained in the resulting [Directory] are packaged in the APK or Bundle.
     * This method is usually only useful for [RegularFile]
     *
     * Some [Artifact] have a [Artifact.name] which cannot be changed. When invoking [toAppendTo] or
     * [toCreate] after this method will throw an exception if trying to set such a file or
     * directory name when the target's [Artifact] [Artifact.name] is not null.
     *
     * @param name file or directory name
     * @return itself
     */
    fun withName(name: String): OutOperationRequest<FileTypeT>

    /**
     * Initiates an append request to a [Artifact.Multiple] artifact type.
     *
     * @param type The [Artifact] of [FileTypeT] identifying the artifact to append to.
     *
     * The artifact type must be [Artifact.Multiple] and [Artifact.Appendable].
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
     * and an ArtifactType defined as follows:
     *
     * ```kotlin
     *     sealed class ArtifactType<T: FileSystemLocation>(val kind: ArtifactKind) {
     *          object MULTIPLE_FILE_ARTIFACT:
     *                  ArtifactType<RegularFile>(FILE), Multiple, Appendable
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
            where ArtifactTypeT : Artifact.Multiple<FileTypeT>,
                  ArtifactTypeT : Artifact.Appendable

    /**
     * Initiates a creation request for a single [Artifact.Replaceable] artifact type.
     *
     * @param type The [Artifact] of [FileTypeT] identifying the artifact to replace.
     *
     * The artifact type must be [Artifact.Replaceable]
     *
     * A creation request does not care about the existing producer, since it replaces the existing
     * producer. Therefore, the existing producer task will not execute (unless it produces other
     * outputs). Please note that when such replace requests are made, the [Task] will replace
     * initial AGP providers.
     *
     * You cannot replace the [Artifact.Multiple] artifact type; therefore, you must instead
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
     * A [SingleArtifact] is defined as follows:
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
            where ArtifactTypeT : Artifact.Single<FileTypeT>,
                  ArtifactTypeT : Artifact.Replaceable

    /**
     * Initiates a reactive request where the [Task] used in the [Artifacts.use] method will be
     * executed once the final version of the [type] artifact has been produced.
     *
     * The final version artifact will be injected in the [Task].
     *
     * Remember that an artifact is not always produced, for instance, an artifact related to
     * minification will not be produced unless minification is turned on.
     *
     * When an artifact is not produced in the current build flow, the [Task] will NOT be
     * invoked.
     *
     * For example, let's take a [Task] that wants to listen to the merged manifest file production :
     *
     * ```kotlin
     *     abstract class MyTask: DefaultTask() {
     *          @get:InputFile abstract val mergedManifestFile: RegularFileProperty
     *
     *          @TaskAction fun taskAction() {
     *              ... verify that manifest is correct ...
     *          }
     *     }
     *
     * You can register the Task that consumes the manifest file as a [org.gradle.api.file.RegularFile]:
     *
     * ```kotlin
     *     val taskProvider= projects.tasks.register(MyTask::class.java, "verifyManifestTask")
     *     artifacts.use(taskProvider)
     *      .wiredWith(MyTask::mergedManifestFile)
     *      .toListenTo(SingleArtifact.MERGED_MANIFEST)
     * ```
     *
     * [Task] will be registered as a finalizer task for the final producer of the [type]
     * artifact using Gradle's [Task.finalizedBy] API.
     *
     * @param type the [Artifact.Single] artifact identifier.
     */
    fun <ArtifactTypeT> toListenTo(type: ArtifactTypeT)
            where ArtifactTypeT : Artifact.Single<FileTypeT>
}

/**
 * Read-only Operations performed by a [Task] with a multiple [RegularFile] or [Directory] output.
 *
 * All APIs will use a [Artifact.Multiple] as a target type and must use a [ListProperty] of
 * [FileTypeT] to retrieve the artifacts.
 */
interface MultipleArtifactTypeOutOperationRequest<FileTypeT: FileSystemLocation> {

    /**
     * Initiates a reactive request where the [Task] used in the [Artifacts.use] method will be
     * executed once the final version of the [type] artifact has been produced.
     *
     * The final version artifact will be injected in the [Task].
     *
     * Remember that an artifact is not always produced, for instance, an artifact related to
     * minification will not be produced unless minification is turned on.
     *
     * When an artifact is not produced in the current build flow, the [Task] will NOT be
     * invoked.
     *
     * For example, let's take a [Task] that wants to listen to the merged manifest file production :
     *
     * ```kotlin
     *     abstract class MyTask: DefaultTask() {
     *          @get:InputFiles abstract val proguardFiles: ListProperty<RegularFile>
     *
     *          @TaskAction fun taskAction() {
     *              ... verify that those proguard files are correct ...
     *          }
     *     }
     *
     * You can register the Task that consumes the proguard files as a [ListProperty] of
     * [org.gradle.api.file.RegularFile]:
     *
     * ```kotlin
     *     val taskProvider= projects.tasks.register(MyTask::class.java, "verifyKeepProguardTask")
     *     artifacts.use(taskProvider)
     *      .wiredWithMultiple(MyTask::proguardFiles)
     *      .toListenTo(MultipleArtifact.MULTIDEX_KEEP_PROGUARD)
     * ```
     *
     * [Task] will be registered as a finalizer task for the final producer of the [type]
     * artifact using Gradle's [Task.finalizedBy] API.
     *
     * @param type the [Artifact.Multiple] artifact identifier.
     */
    fun <ArtifactTypeT> toListenTo(type: ArtifactTypeT)
            where ArtifactTypeT : Artifact.Multiple<FileTypeT>
}

/**
 * Operations performed by a [Task] with a single [RegularFile] output.
 *
 * [Task] is consuming existing version of the target [SingleArtifact] and producing a new version.
 */
interface InAndOutFileOperationRequest {

    /**
     * Sets the output file name for the transformed artifact.
     *
     * @param name file name in the output folder.
     * @return itself
     */
    fun withName(name: String): InAndOutFileOperationRequest

    /**
     * Initiates a transform request to a single [Artifact.Transformable] artifact type.
     *
     * @param type The [Artifact] identifying the artifact to transform. The [Artifact]'s
     * [Artifact.kind] must be [Artifact.FILE].
     *
     * The artifact type must be [Artifact.Single] and [Artifact.Transformable].
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
     * An ArtifactType defined as follows:
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
        where ArtifactTypeT: Artifact.Single<RegularFile>,
              ArtifactTypeT: Artifact.Transformable
}

/**
 * Operations performed by a [Task] with a single [Directory] or [RegularFile] output.
 *
 * [Task] is consuming existing version of the target [MultipleArtifact] and producing a new version.
 */
interface CombiningOperationRequest<FileTypeT: FileSystemLocation> {
    /**
     * Initiates a transform request to a multiple [Artifact.Transformable] artifact type.
     *
     * @param type The [Artifact] of [FileTypeT] identifying the artifact to transform.
     *
     * The artifact type must be [Artifact.Multiple] and [Artifact.Transformable].
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
     * A [MultipleArtifact] is defined as follows:
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
        where ArtifactTypeT: Artifact.Multiple<FileTypeT>,
              ArtifactTypeT: Artifact.Transformable
}

/**
 * Operations performed by a [Task] with a single [Directory] output.
 *
 * [Task] is consuming existing version of the target [SingleArtifact] and producing a new version.
 */
interface InAndOutDirectoryOperationRequest<TaskT : Task> {

    /**
     * Initiates a transform request to a single [Artifact.Transformable] artifact type.
     *
     * @param type The [Artifact] identifying the artifact to transform. The [Artifact]'s
     * [Artifact.kind] must be [Artifact.DIRECTORY]
     *
     * The artifact type must be [Artifact.Single] and [Artifact.Transformable].
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
     * An ArtifactType defined as follows:
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
     *          MyTask::inputDir,
     *          MyTask::outputDir)
     *      .toTransform(ArtifactType.SINGLE_DIR_ARTIFACT)
     * ```
     */
    fun <ArtifactTypeT> toTransform(type: ArtifactTypeT)
        where ArtifactTypeT: Artifact.Single<Directory>,
              ArtifactTypeT: Artifact.Transformable

    /**
     * Initiates a transform request to a single [Artifact.Transformable] artifact type that can
     * contain more than one artifact.
     *
     * @param type The [Artifact] of the [Directory] identifying the artifact to transform.
     * @return [ArtifactTransformationRequest] that will allow processing of individual artifacts
     * located in the input directory.
     *
     * The artifact type must be [Artifact.Single], [Artifact.Transformable],
     * and [Artifact.ContainsMany].
     *
     * For example, let's take a [Task] to transform a list of [org.gradle.api.file.RegularFile] as
     * inputs into a single output:
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
     *       .wiredWithDirectories(
     *          MyTask::inputFolder,
     *          MyTask::outputFolder)
     *       .toTransformMany(SingleArtifact.APK)
     *     taskProvider.configure { task ->
     *          task.getTransformationRequest().set(transformationRequest)
     *     }
     * ```
     */
    fun <ArtifactTypeT> toTransformMany(type: ArtifactTypeT): ArtifactTransformationRequest<TaskT>
        where ArtifactTypeT: Artifact.Single<Directory>,
              ArtifactTypeT: Artifact.ContainsMany,
              ArtifactTypeT: Artifact.Transformable
}
