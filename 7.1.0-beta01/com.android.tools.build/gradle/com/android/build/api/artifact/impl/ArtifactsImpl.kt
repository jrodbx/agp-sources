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

package com.android.build.api.artifact.impl

import com.android.build.api.artifact.ArtifactKind
import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.Artifact.Multiple
import com.android.build.api.artifact.Artifact.Single
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.Artifacts
import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.variant.BuiltArtifactsLoader
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.scope.getOutputPath
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.Collections

/**
 * Implementation of the [Artifacts] Variant API interface.
 *
 * Implementation will delegate most of its [FileSystemLocationProperty] handling to
 * [ArtifactContainer] only handling the [TaskProvider] relationships.
 *
 * This call will also contains some AGP private methods for added services to support AGP
 * specific services like setting initial AGP providers.
 */
class ArtifactsImpl(
    project: Project,
    private val identifier: String
): Artifacts {

    private val storageProvider = StorageProviderImpl()
    private val objects= project.objects
    internal val buildDirectory = project.layout.buildDirectory
    private val outstandingRequests = Collections.synchronizedList(ArrayList<ArtifactOperationRequest>())

    override fun getBuiltArtifactsLoader(): BuiltArtifactsLoader {
        return BuiltArtifactsLoaderImpl()
    }

    override fun <FILE_TYPE : FileSystemLocation> get(
        type: SingleArtifact<FILE_TYPE>
    ): Provider<FILE_TYPE> = getArtifactContainer(type).get()

    fun <FILE_TYPE : FileSystemLocation> get(
        type: Single<FILE_TYPE>
    ): Provider<FILE_TYPE> = getArtifactContainer(type).get()

    override fun <FileTypeT : FileSystemLocation> getAll(
        type: MultipleArtifact<FileTypeT>
    ): Provider<List<FileTypeT>> = getArtifactContainer(type).get()

    fun <FILE_TYPE : FileSystemLocation> getAll(
        type: Multiple<FILE_TYPE>
    ): Provider<List<FILE_TYPE>>
            = getArtifactContainer(type).get()

    override fun <TaskT : Task> use(taskProvider: TaskProvider<TaskT>): TaskBasedOperationImpl<TaskT> {
        return TaskBasedOperationImpl(objects, this, taskProvider).also {
            outstandingRequests.add(it)
        }
    }

    // End of public API implementation, start of private AGP services.

    /**
     * Returns a [File] representing the artifact type location (could be a directory or regular
     * file).
     */
    internal fun <T: FileSystemLocation> getOutputPath(type: Artifact<T>, vararg paths: String)=
        type.getOutputPath(buildDirectory, identifier, *paths)

    /**
     * Returns a [RegularFile] that can be used to output a [Single]
     * @param type expect output [SingleArtifact]
     * @param paths the extra folder to add when constructing the file path.
     */
    internal fun getOutputRegularFile(
            type: Single<RegularFile>,
            vararg paths: String) =
            buildDirectory.file(type.getOutputPath(buildDirectory, identifier, *paths).absolutePath)

    /**
     * Returns the [ArtifactContainer] for the passed [type]. The instance may be allocated as part
     * of the call if there is not [ArtifactContainer] for this [type] registered yet.
     *
     * @param type requested artifact type
     * @return the [ArtifactContainer] for the passed type
     */
    internal fun <FILE_TYPE : FileSystemLocation> getArtifactContainer(
        type: Single<FILE_TYPE>
    ): SingleArtifactContainer<FILE_TYPE> {

        return storageProvider.getStorage(type.kind).getArtifact(objects, type)
    }

    /**
     * Returns the [ArtifactContainer] for the passed [type]. The instance may be allocated as part
     * of the call if there is not [ArtifactContainer] for this [type] registered yet.
     *
     * @param type requested artifact type
     * @return the [ArtifactContainer] for the passed type
     */
    internal fun <FILE_TYPE : FileSystemLocation> getArtifactContainer(
        type: Multiple <FILE_TYPE>
    ): MultipleArtifactContainer<FILE_TYPE> {

        return storageProvider.getStorage(type.kind).getArtifact(objects, type)
    }

    fun <T: FileSystemLocation> republish(
        source: Single<T>,
        target: Single<T>) {
        storageProvider.getStorage(target.kind).copy(target, getArtifactContainer(source))
    }

    /**
     * Sets the Android Gradle Plugin producer. Although conceptually the AGP producers are first
     * to process/consume artifacts, we want to register them last after all custom code has had
     * opportunities to transform or replace it.
     *
     * Therefore, we cannot rely on the usual append/replace pattern but instead use this API to
     * be artificially put first in the list of producers.
     *
     * @param taskProvider the [TaskProvider] for the task producing the artifact
     * @param property: the field reference to retrieve the output from the task
     */
    @JvmName("setInitialProvider")
    internal fun <FILE_TYPE, TASK> setInitialProvider(
        taskProvider: TaskProvider<TASK>,
        property: (TASK) -> FileSystemLocationProperty<FILE_TYPE>
    ): SingleInitialProviderRequestImpl<TASK, FILE_TYPE>
            where FILE_TYPE : FileSystemLocation,
                  TASK: Task
            = SingleInitialProviderRequestImpl(this, taskProvider, property)

    /**
     * Adds an Android Gradle Plugin producer.
     *
     * The passed [type] must be a [Multiple] that accepts more than one producer.
     *
     * Although conceptually the AGP producers are first to produce artifacts, we want to register
     * them last after all custom code had the opportunity to transform or replace it.
     *
     * Therefore, we cannot rely on the usual append/replace pattern but instead use this API to
     * be artificially put first in the list of producers for the passed [type]
     *
     * @param type the [Multiple] artifact type being produced
     * @param taskProvider the [TaskProvider] for the task producing the artifact
     * @param property: the field reference to retrieve the output from the task
     */
    internal fun <FILE_TYPE: FileSystemLocation, TASK: Task> addInitialProvider(
        type: Multiple<FILE_TYPE>,
        taskProvider: TaskProvider<TASK>,
        property: (TASK) -> FileSystemLocationProperty<FILE_TYPE>
    ) {
        val artifactContainer = getArtifactContainer(type)
        taskProvider.configure {
            // since the taskProvider will execute, resolve its output path, and since there can
            // be multiple ones, just put the task name at all times.
            property(it).set(type.getOutputPath(buildDirectory, identifier, taskProvider.name))
        }
        artifactContainer.addInitialProvider(taskProvider.flatMap { property(it) })
    }

    /**
     * Sets a [Property] value to the final producer for the given artifact type.
     *
     * If there are more than one producer appending artifacts for the passed type, calling this
     * method will generate an error.
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
    fun <T: FileSystemLocation> setTaskInputToFinalProduct(
        artifactType: Single<T>, taskInputProperty: Property<T>
    ) {
        val finalProduct = get(artifactType)
        taskInputProperty.setDisallowChanges(finalProduct)
    }

    fun <FILE_TYPE : FileSystemLocation> copy(
        artifactType: Single<FILE_TYPE>,
        from: ArtifactsImpl
    ) {
        val artifactContainer = from.getArtifactContainer(artifactType)
        storageProvider.getStorage(artifactType.kind).copy(artifactType, artifactContainer)
    }

    fun <FILE_TYPE : FileSystemLocation> copy(
        artifactType: Multiple<FILE_TYPE>,
        from: ArtifactsImpl
    ) {
        val artifactContainer = from.getArtifactContainer(artifactType)
        storageProvider.getStorage(artifactType.kind).copy(artifactType, artifactContainer)
    }

    /**
     * Appends a single [Provider] of [T] to a [Artifact.Multiple] of <T>
     *
     * @param type the multiple type to append to.
     * @param element the element to add.
     */
    fun <T: FileSystemLocation> appendTo(type: Artifact.Multiple<T>, element: Provider<T>) {
        getArtifactContainer(type).addInitialProvider(element)
    }
    /**
     * Appends a [List] of [Provider] of [T] to a [MultipleArtifactType] of <T>
     *
     * @param type the multiple type to append to.
     * @param elements the list of elements to add.
     */
    fun <T: FileSystemLocation> appendAll(type: Artifact.Multiple<T>, elements: Provider<List<T>>) {
        getArtifactContainer(type).setInitialProvider(elements);
    }

    private val allClasses = project.files().from(
        getAll(MultipleArtifact.ALL_CLASSES_DIRS),
        getAll(MultipleArtifact.ALL_CLASSES_JARS)
    )

    /**
     * The current [FileCollection] for [InternalMultipleArtifactType.ALL_CLASSES_JARS]
     * and [InternalMultipleArtifactType.ALL_CLASSES_DIRS]
     * The returned file collection is final but its content can change.
     */
    fun getAllClasses(): FileCollection = allClasses

    fun addRequest(request: ArtifactOperationRequest) {
        outstandingRequests.add(request)
    }

    fun closeRequest(request: ArtifactOperationRequest) {
        outstandingRequests.remove(request)
    }

    fun ensureAllOperationsAreSatisfied() {
        if (outstandingRequests.isEmpty()) return
        throw RuntimeException(outstandingRequests.joinToString(
            separator = "\n\t",
            prefix = "The following Variant API operations are incomplete :\n",
            transform = ArtifactOperationRequest::description,
            postfix = "\nMake sure to correctly chain all calls."
        ))
    }
}


internal class SingleInitialProviderRequestImpl<TASK: Task, FILE_TYPE: FileSystemLocation>(
    private val artifactsImpl: ArtifactsImpl,
    private val taskProvider: TaskProvider<TASK>,
    private val from: (TASK) -> FileSystemLocationProperty<FILE_TYPE>
) {
    var fileName: String? = null
    private var buildOutputLocation: String? = null
    private var buildOutputLocationResolver: ((TASK) -> Provider<Directory>)? = null

    /**
     * Internal API to set the location of the directory where the produced [FILE_TYPE] should
     * be located in.
     *
     * @param location a directory absolute path
     */
    fun atLocation(location: String?): SingleInitialProviderRequestImpl<TASK, FILE_TYPE> {
        buildOutputLocation = location
        return this
    }

    /**
     * Internal API to set the location of the directory where the produced [FILE_TYPE] should be
     * located in.
     *
     * @param location a method reference on the [TASK] to return a [Provider<Directory>]
     */
    fun atLocation(location: (TASK) -> Provider<Directory>)
            : SingleInitialProviderRequestImpl<TASK, FILE_TYPE> {
        buildOutputLocationResolver = location
        return this
    }

    fun withName(name: String): SingleInitialProviderRequestImpl<TASK, FILE_TYPE> {
        fileName = name
        return this
    }

    fun on(type: Single<FILE_TYPE>) {

        val artifactContainer = artifactsImpl.getArtifactContainer(type)
        taskProvider.configure {
            // Regular-file artifacts require a file name (see bug 151076862)
            if (type.kind is ArtifactKind.FILE
                && fileName.isNullOrEmpty()
                && type.getFileSystemLocationName().isNullOrEmpty()) {
                fileName = DEFAULT_FILE_NAME_OF_REGULAR_FILE_ARTIFACTS
            }
            val outputAbsolutePath = when {
                buildOutputLocation != null -> {
                    File(buildOutputLocation, fileName.orEmpty())
                }
                buildOutputLocationResolver != null -> {
                    val resolver = buildOutputLocationResolver!!
                    File(resolver.invoke(it).get().asFile, fileName.orEmpty())
                }
                else -> {
                    artifactsImpl.getOutputPath(type,
                        if (artifactContainer.hasCustomProviders()) taskProvider.name else "",
                        fileName.orEmpty())
                }
            }
            // since the taskProvider will execute, resolve its output path.
            from(it).set(outputAbsolutePath)
        }
        artifactContainer.setInitialProvider(taskProvider.flatMap { from(it) })
    }
}

const val DEFAULT_FILE_NAME_OF_REGULAR_FILE_ARTIFACTS = "out"
