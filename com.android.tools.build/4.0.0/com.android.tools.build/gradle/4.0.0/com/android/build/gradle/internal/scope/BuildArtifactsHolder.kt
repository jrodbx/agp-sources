/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.scope

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.ArtifactKind
import com.android.build.api.artifact.impl.OperationsImpl
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.FileReader

typealias Report = Map<String, BuildArtifactsHolder.ProducersData>

/**
 * Buildable artifact holder.
 *
 * This class manages buildable artifacts, allowing users to transform [ArtifactType].
 *
 * @param project the Gradle [Project]
 * @param rootOutputDir a supplier for the intermediate directories to place output files.
 * @parma dslScope the scope for dsl parsing issue raising.
 */
abstract class BuildArtifactsHolder(
    private val project: Project,
    private val rootOutputDir: () -> File,
    identifier: String
) {

    private val operations= OperationsImpl(project.objects, identifier,
        project.layout.buildDirectory)

    fun getOperations(): OperationsImpl = operations

    // because of existing public APIs, we cannot move [AnchorOutputType.ALL_CLASSES] to Provider<>
    private val allClasses= project.files()

    /**
     * Republishes an [ArtifactType] under a different type. This is useful when a level of
     * indirection is used.
     *
     * @param sourceType the original [ArtifactType] for the built artifacts.
     * @param targetType the supplemental [ArtifactType] the same built artifacts will also be
     * published under.
     */
    fun <T: FileSystemLocation, U> republish(sourceType: U, targetType: U)
            where U: ArtifactType<T>, U : ArtifactType.Single {
        operations.republish(sourceType, targetType)
    }

    /**
     * Copies a published [ArtifactType] from another instance of [BuildArtifactsHolder] to this
     * instance.
     * This does not remove the original elements from the source [BuildArtifactsHolder].
     *
     * @param artifactType artifact type to copy to this holder.
     * @param from source [BuildArtifactsHolder] to copy the produced artifacts from.
     */
    fun <T: FileSystemLocation> copy(artifactType: SingleArtifactType<T>, from: BuildArtifactsHolder) {
        copy(artifactType, from, artifactType)
    }

    /**
     * Copies a published [ArtifactType] from another instance of [BuildArtifactsHolder] to this
     * instance.
     * This does not remove the original elements from the source [BuildArtifactsHolder].
     *
     * @param artifactType artifact type to copy to this holder.
     * @param from source [BuildArtifactsHolder] to copy the produced artifacts from.
     * @param originalArtifactType artifact type under which the producers are registered in the
     * source [BuildArtifactsHolder], by default is the same [artifactType]
     */
    fun <T: FileSystemLocation> copy(artifactType: SingleArtifactType<T>, from: BuildArtifactsHolder, originalArtifactType: SingleArtifactType<T> = artifactType) {
        val artifactContainer = from.operations.getArtifactContainer(originalArtifactType)
        operations.copy(artifactType, artifactContainer)
    }

    /**
     * Registers a new [RegularFile] producer for a particular [ArtifactType]. The producer is
     * identified by a [TaskProvider] to avoid configuring the task until the produced [RegularFile]
     * is required by another [Task].
     *
     * The simplest way to use the mechanism is as follow :
     * <pre>
     *     open class MyTask(objectFactory: ObjectFactory): Task() {
     *          val outputFile = objectFactory.fileProperty()
     *     }
     *
     *     val myTaskProvider = taskFactory.register("myTask", MyTask::class.java)
     *
     *     scope.artifacts.producesFile(InternalArtifactType.SOME_ID,
     *            OperationType.INITIAL,
     *            myTaskProvider,
     *            myTaskProvider.map { it -> it.outputFile }
     *            "some_file_name")
     *
     * </pre>
     *
     * Consumers should use [getFinalProduct] or [OperationsImpl.getAll] to get a [Provider] instance
     * for registered products which ensures that [Task]s don't get initialized until the
     * [Provider.get] method is invoked during a consumer task configuration execution for instance.
     *
     * @param artifactType the produced artifact type
     * @param taskProvider the [TaskProvider] for the task ultimately responsible for producing the
     * artifact.
     * @param productProvider the [Provider] of the artifact [RegularFile]
     * @param buildDirectory the destination directory of the produced artifact or not provided if
     * using the default location.
     * @param fileName the desired file name, must be provided.
     */
    fun <T: Task, ARTIFACT_TYPE> producesFile(
        artifactType: ARTIFACT_TYPE,
        taskProvider: TaskProvider<out T>,
        productProvider: (T) -> RegularFileProperty,
        buildDirectory: String? = null,
        fileName: String
    ) where ARTIFACT_TYPE : ArtifactType<RegularFile>,
            ARTIFACT_TYPE : ArtifactType.Single {

        operations.setInitialProvider(
            taskProvider,
            productProvider)
            .atLocation(buildDirectory)
            .withName(fileName)
            .on(artifactType)
    }

    /**
     * Registers a new [RegularFile] producer for a particular [ArtifactType]. The producer is
     * identified by a [TaskProvider] to avoid configuring the task until the produced [RegularFile]
     * is required by another [Task].
     *
     * The passed [productProvider] returns a [Provider] which mean that the output location cannot
     * be changed and will be set by the task itself or during its configuration.
     */
    fun <T: Task, ARTIFACT_TYPE> producesFile(
        artifactType: ARTIFACT_TYPE,
        taskProvider: TaskProvider<out T>,
        productProvider: (T) -> Provider<RegularFile>
    )
            where ARTIFACT_TYPE : ArtifactType<RegularFile>,
                  ARTIFACT_TYPE : ArtifactType.Single {

        val propertyProvider = { task : T ->
            val property = project.objects.fileProperty()
            property.set(productProvider(task))
            property
        }
        producesFile(
            artifactType,
            taskProvider,
            propertyProvider,
            "",
            ""
        )
    }

    /**
     * Registers a new [Directory] producer for a particular [ArtifactType]. The producer is
     * identified by a [TaskProvider] to avoid configuring the task until the produced [Directory]
     * is required by another [Task].
     *
     * The simplest way to use the mechanism is as follow :
     * <pre>
     *     open class MyTask(objectFactory: ObjectFactory): Task() {
     *          val outputFile = objectFactory.directoryProperty()
     *     }
     *
     *     val myTaskProvider = taskFactory.register("myTask", MyTask::class.java)
     *
     *     scope.artifacts.producesDir(InternalArtifactType.SOME_ID,
     *            OperationType.INITIAL,
     *            myTaskProvider,
     *            myTaskProvider.map { it -> it.outputFile }
     *            "some_file_name")
     *
     * </pre>
     *
     * Consumers should use [getFinalProduct] or [OperationsImpl.getAll] to get a [Provider] instance
     * for registered products which ensures that [Task]s don't get initialized until the
     * [Provider.get] method is invoked during a consumer task configuration execution for instance.
     *
     * @param artifactType the produced artifact type
     * @param taskProvider the [TaskProvider] for the task ultimately responsible for producing the
     * artifact.
     * @param productProvider the [Provider] of the artifact [Directory]
     * @param buildDirectory the destination directory of the produced artifact or not provided if
     * using the default location.
     * @param fileName the desired directory name or empty string if no sub-directory should be
     * used.
     */
    fun <T: Task, ARTIFACT_TYPE> producesDir(
        artifactType: ARTIFACT_TYPE,
        taskProvider: TaskProvider<out T>,
        productProvider: (T) -> DirectoryProperty,
        buildDirectory: String? = null,
        fileName: String = "out"
    ) where ARTIFACT_TYPE : ArtifactType<Directory>,
            ARTIFACT_TYPE : ArtifactType.Single {

        operations.setInitialProvider(
            taskProvider,
            productProvider
        ).atLocation(buildDirectory).withName(fileName).on(artifactType)
    }

    // TODO : remove these 2 APIs once all java tasks stopped using those after Kotlin translation.
    fun <T: Task, ARTIFACT_TYPE> producesFile(
        artifactType: ARTIFACT_TYPE,
        taskProvider: TaskProvider<out T>,
        productProvider: (T) -> RegularFileProperty,
        fileName: String = "out"
    )
            where ARTIFACT_TYPE : ArtifactType<RegularFile>,
                  ARTIFACT_TYPE : ArtifactType.Single

            = producesFile(artifactType, taskProvider, productProvider, null, fileName)


    fun <T: Task, ARTIFACT_TYPE> producesDir(
        artifactType: ARTIFACT_TYPE,
        taskProvider: TaskProvider<out T>,
        propertyProvider: (T) -> DirectoryProperty,
        fileName: String = "out"
    ) where ARTIFACT_TYPE : ArtifactType<Directory>,
            ARTIFACT_TYPE : ArtifactType.Single
            = producesDir(artifactType, taskProvider, propertyProvider, null, fileName)

    /**
     * Returns true if there is at least one producer for the passed [ArtifactType]
     *
     * @param artifactType the identifier for the built artifact.
     */
    fun <T: FileSystemLocation, ARTIFACT_TYPE> hasFinalProduct(artifactType: ARTIFACT_TYPE)
        where ARTIFACT_TYPE: ArtifactType<T>,
              ARTIFACT_TYPE: ArtifactType.Single
            = !operations.getArtifactContainer(artifactType).needInitialProducer().get()

    fun <T: FileSystemLocation, ARTIFACT_TYPE> hasFinalProducts(artifactType: ARTIFACT_TYPE)
            where ARTIFACT_TYPE: ArtifactType<T>,
                  ARTIFACT_TYPE: ArtifactType.Multiple
            = !operations.getArtifactContainer(artifactType).needInitialProducer().get()


    /**
     * Returns a [Provider] of either a [Directory] or a [RegularFile] depending on the passed
     * [ArtifactKind]. The [Provider] will represent the final value of the built artifact
     * irrespective of when this call is made.
     *
     * The simplest way to use the mechanism is as follow :
     * <pre>
     *     open class MyTask(objectFactory: ObjectFactory): Task() {
     *          val inputFile: Provider<RegularFile>
     *     }
     *
     *     val myTaskProvider = taskFactory.register("myTask", MyTask::class.java) {
     *          it.inputFile = scope.artifacts.getFinalProduct(InternalArtifactTYpe.SOME_ID)
     *     }
     * </pre>
     *
     * @param artifactType the identifier for the built artifact.
     */
    fun <T: FileSystemLocation, ARTIFACT_TYPE> getFinalProduct(
        artifactType: ARTIFACT_TYPE): Provider<T>
        where ARTIFACT_TYPE: ArtifactType<T>,
              ARTIFACT_TYPE: ArtifactType.Single {

        return operations.get(artifactType)
    }

    /**
     * Returns a [Provider] of a [FileCollection] for the passed [ArtifactType].
     * The [FileCollection] will represent the final value of the built artifact irrespective of
     * when this call is made as long as the [Provider] is resolved at execution time.
     *
     * @param  artifactType the identifier for thje built artifact.
     */
    fun <T: FileSystemLocation, ARTIFACT_TYPE> getFinalProductAsFileCollection(artifactType: ARTIFACT_TYPE): Provider<FileCollection>
        where ARTIFACT_TYPE:  ArtifactType<T>,
              ARTIFACT_TYPE: ArtifactType.Single {

        return project.provider {
            if (artifactType == AnchorOutputType.ALL_CLASSES) {
                getAllClasses()
            } else {
                if (hasFinalProduct(artifactType)) {
                    project.files(getFinalProduct(artifactType))
                } else project.files()
            }
        }
    }

    /**
     * Sets a [Property] value to the final producer for the given artifact type.
     *
     * If there are more than one producer appending artifacts for the passed type, calling this
     * method will generate an error and [setTaskInputToFinalProducts] should be used instead.
     *
     * The simplest way to use the mechanism is as follow :
     * <pre>
     *     abstract class MyTask: Task() {
     *          @InputFile
     *          abstract val inputFile: RegularFileProperty
     *     }
     *
     *     val myTaskProvider = taskFactory.register("myTask", MyTask::class.java) {
     *          scope.artifacts.setTaskInputToFinalProduct(InternalArtifactTYpe.SOME_ID, it.inputFile)
     *     }
     * </pre>
     *
     * @param artifactType requested artifact type
     * @param taskInputProperty the [Property] to set the final producer on.
     */
    fun <T: FileSystemLocation, ARTIFACT_TYPE> setTaskInputToFinalProduct(
        artifactType: ARTIFACT_TYPE, taskInputProperty: Property<T>)
        where ARTIFACT_TYPE: ArtifactType<T>, ARTIFACT_TYPE: ArtifactType.Single {
        val finalProduct = getFinalProduct(artifactType)
        taskInputProperty.setDisallowChanges(finalProduct)
    }

    /**
     * Appends a [FileCollection] to the [AnchorOutputType.ALL_CLASSES] artifact.
     *
     * @param files the [FileCollection] to add.
     */
    fun appendToAllClasses(files: FileCollection) {
        synchronized(allClasses) {
            allClasses.from(files)
        }
    }

    /**
     * Returns the current [FileCollection] for [AnchorOutputType.ALL_CLASSES] as of now.
     * The returned file collection is final but its content can change.
     */
    fun getAllClasses(): FileCollection = allClasses

    // TODO: Reimplement or remove feature.
    fun createReport(): Report = mapOf()

    /** A data class for use with GSON. */
    data class ProducerData(
        @SerializedName("files")
        val files: List<String>,
        @SerializedName("builtBy")
        val builtBy : String
    )

    data class ProducersData(
        @SerializedName("producer")
        val producers: List<ProducerData>
    )

    companion object {

        fun parseReport(file : File) : Report {
            val result = mutableMapOf<String, ProducersData>()
            val parser = JsonParser()
            FileReader(file).use { reader ->
                for ((key, value) in parser.parse(reader).asJsonObject.entrySet()) {
                    val obj = value.asJsonObject
                    val producers = obj.getAsJsonArray("producer").map {
                        ProducerData(
                            it.asJsonObject.getAsJsonArray("files").map {
                                it.asString
                            },
                            it.asJsonObject.get("builtBy").asString
                        )
                    }

                    result[key] = ProducersData(producers)
                }
            }
            return result
        }
    }
}
