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
import com.android.build.api.artifact.Artifact.Category.INTERMEDIATES
import com.android.build.api.artifact.Artifact.Multiple
import com.android.build.api.artifact.Artifact.Single
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.Artifacts
import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.variant.BuiltArtifactsLoader
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.impl.DeferredActionManager
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.getIntermediateOutputPath
import com.android.build.gradle.internal.scope.getOutputPath
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.utils.FileUtils
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.Collections
import  org.gradle.api.model.ObjectFactory

/**
 * Implementation of the [Artifacts] Variant API interface.
 *
 * Implementation will delegate most of its [FileSystemLocationProperty] handling to
 * [ArtifactContainer] only handling the [TaskProvider] relationships.
 *
 * This call will also contain some AGP private methods for added services to support AGP
 * specific services like setting initial AGP providers.
 */
class ArtifactsImpl(
    project: Project,
    private val identifier: String
): Artifacts {

    private val storageProvider = StorageProviderImpl()
    internal val listenerManager = DeferredActionManager()
    val objects: ObjectFactory = project.objects
    internal val buildDirectory = project.layout.buildDirectory
    private val outstandingRequests = Collections.synchronizedList(ArrayList<ArtifactOperationRequest>())
    val logger = project.logger
    val taskContainer = project.tasks

    override fun getBuiltArtifactsLoader(): BuiltArtifactsLoader {
        return BuiltArtifactsLoaderImpl()
    }

    private val publicScopedArtifacts : Map<ScopedArtifacts.Scope, ScopedArtifactsImpl>
    private val internalScopedArtifacts : Map<InternalScopedArtifacts.InternalScope, ScopedArtifactsImpl>

    init {
        publicScopedArtifacts = ScopedArtifacts.Scope.values().associateWith {
            ScopedArtifactsImpl(
                it.name,
                identifier,
                project.layout,
                project::files
            )
        }

        internalScopedArtifacts = InternalScopedArtifacts.InternalScope.values().associateWith {
            ScopedArtifactsImpl(
                it.name,
                identifier,
                project.layout,
                project::files
            )
        }

        publicScopedArtifacts[ScopedArtifacts.Scope.PROJECT]?.let {
            it.setInitialContent(
                ScopedArtifact.JAVA_RES,
                this,
                InternalArtifactType.MERGED_JAVA_RES
            )
        }
    }

    override fun forScope(scope: ScopedArtifacts.Scope): ScopedArtifactsImpl =
        publicScopedArtifacts[scope] ?:
            throw IllegalArgumentException("${scope.name} is not implemented yet !")

    /**
     * Returns a [ScopedArtifactsImpl] for internal scope defined by the passed [scope] parameter.
     */
    fun forScope(scope: InternalScopedArtifacts.InternalScope): ScopedArtifactsImpl =
        internalScopedArtifacts[scope]
            ?: throw java.lang.IllegalArgumentException("${scope.name} not supported")

    override fun <FILE_TYPE : FileSystemLocation> get(
        type: SingleArtifact<FILE_TYPE>
    ): Provider<FILE_TYPE>  = getArtifactContainer(type).get()

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

    fun <FileTypeT: FileSystemLocation> add(
            type: Multiple<FileTypeT>,
            artifact: FileTypeT) {

        addStaticProvider(
            getArtifactContainer(type),
            type,
            artifact,
        )
    }

    override fun <FileTypeT : FileSystemLocation> add(
            type: MultipleArtifact<FileTypeT>,
            artifact: FileTypeT) {

        addStaticProvider(
            getArtifactContainer(type),
            type,
            artifact
        )
    }

    override fun <MultipleArtifactT> addStaticDirectory(
        type: MultipleArtifactT,
        inputLocation: Directory
    ) where MultipleArtifactT: MultipleArtifact<Directory>,
            MultipleArtifactT: Appendable {
        addStaticProvider(getArtifactContainer(type), type, inputLocation)
    }

    override fun <TaskT : Task> use(taskProvider: TaskProvider<TaskT>): TaskBasedOperationImpl<TaskT> {
        return TaskBasedOperationImpl(objects, this, taskProvider).also {
            outstandingRequests.add(it)
        }
    }

    // End of public API implementation, start of private AGP services.

    /**
     * Returns a [File] representing the artifact type location (could be a directory or regular
     * file). forceFilename is to overwrite default fileName
     */
    internal fun <T: FileSystemLocation> getOutputPath(type: Artifact<T>, vararg paths: String, forceFilename:String? = null)=
        type.getOutputPath(buildDirectory, identifier, *paths, forceFilename = forceFilename)

    private fun <T: FileSystemLocation> getIntermediateOutputPath(type: Artifact<T>, vararg paths: String, forceFilename:String? = null)=
        type.getIntermediateOutputPath(buildDirectory, identifier, *paths, forceFilename = forceFilename)

    fun calculateOutputPath(type: Single<*>, task: Task, fileName: String? = null): File {
        with(getArtifactContainer(type)) {
            return when {
                type.category == INTERMEDIATES && namingContext?.getOutputLocation() == null ->
                    generateIntermediatePath(type, task, false, fileName)
                //  this switching is for non-intermediate artifacts is safe as the final artifact
                //  ends up in a completely separate directory structure
                getFinalProvider() == null || task.name == getFinalProvider()?.name -> generateDefaultPath(type, fileName)
                else -> generateIntermediatePath(type, task, true, fileName) // for outputs type non-final transformations
            }
        }
    }

    /**
     * Intermediate path always has task name in it
     */
    private fun generateIntermediatePath(
        type: Single<*>,
        task: Task,
        ignorePredefinedLocation:Boolean,
        fileName: String?): File {
        val container = getArtifactContainer(type)
        val fileName = calculateFileName(fileName, type)
        val output = container.namingContext?.getOutputLocation()
        return if (output == null || ignorePredefinedLocation)
            getIntermediateOutputPath(
                type,
                task.name,
                forceFilename = fileName
            )
        else
            FileUtils.join(output, fileName)

    }

    private fun generateDefaultPath(type: Single<*>, fileName: String?): File {
        with(getArtifactContainer(type)) {
            val fileName = calculateFileName(fileName, type)
            val output = namingContext?.getOutputLocation()
            return if (output != null)
            //final transformer with
                FileUtils.join(output, fileName)
            else
                getOutputPath(type, forceFilename = fileName)
        }
    }

    private fun calculateFileName(fileName: String?, type: Single<*>): String =
        fileName ?: getArtifactContainer(type).namingContext?.getFilename() ?: calculateFileName(type)


    private fun calculateFileName(type: Single<*>, ): String {
        if (type.kind is ArtifactKind.FILE) {
            return if (type.getFileSystemLocationName().isNotEmpty())
                type.getFileSystemLocationName()
            else
                DEFAULT_FILE_NAME_OF_REGULAR_FILE_ARTIFACTS
        }
        return ""
    }

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

    private fun <T: FileSystemLocation> republish(
        source: Multiple<T>,
        target: Multiple<T>) {
        storageProvider.getStorage(target.kind).copy(target, getArtifactContainer(source))
    }

    /**
     * Sets the Android Gradle Plugin producer. Although conceptually the AGP producers are first
     * to process/consume artifacts, we want to register them last after all custom cod`e has had
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

    @JvmName("setInitialProvider")
    internal fun <FILE_TYPE, TASK> setInitialProvider(
        taskProvider: TaskProvider<TASK>,
        property: (TASK) -> FileSystemLocationProperty<FILE_TYPE>,
        customProvider: ((TASK) -> Provider<FILE_TYPE>)
    ): SingleInitialProviderRequestImpl<TASK, FILE_TYPE>
            where FILE_TYPE : FileSystemLocation,
                  TASK: Task
            = SingleInitialProviderRequestImpl(this, taskProvider, property, customProvider)


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
        artifactContainer.addInitialProvider(taskProvider, taskProvider.flatMap { property(it) })
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
    fun <T: FileSystemLocation> appendTo(type: Multiple<T>, from: Single<T>) {
        getArtifactContainer(type).transferFrom(this, from)
    }

    /**
     * Appends a single [Provider] of [T] to a [ScopedArtifact] under the
     * [ScopedArtifacts.Scope.PROJECT] scope.
     *
     * @param type the multiple type to append to.
     * @param from the element to add.
     */
    fun <T: FileSystemLocation> appendTo(type: ScopedArtifact, from: Single<T>) {
        forScope(ScopedArtifacts.Scope.PROJECT)
            .setInitialContent(type, this, from)
    }

    /**
     * Appends a [List] of [Provider] of [T] to a [MultipleArtifactType] of <T>
     *
     * @param type the multiple type to append to.
     * @param elements the list of elements to add.
     */
    fun <T: FileSystemLocation> appendAll(type: Multiple<T>, elements: Provider<List<T>>) {
        getArtifactContainer(type).addInitialProvider(listOf(), elements);
    }

    fun addRequest(request: ArtifactOperationRequest) {
        outstandingRequests.add(request)
    }

    fun closeRequest(request: ArtifactOperationRequest) {
        outstandingRequests.remove(request)
    }

    /**
     * Finalize the artifact's container.
     *
     * Make sure all pending requests have been fully configured.
     * Finalize all artifact type.
     * TODO: lock the container so users cannot invoke any of the variant API methods on the
     * artifacts.
     */
    internal fun finalizeAndLock() {
        if (outstandingRequests.isEmpty()) return
        throw RuntimeException(outstandingRequests.joinToString(
            separator = "\n\t",
            prefix = "The following Variant API operations are incomplete :\n",
            transform = ArtifactOperationRequest::description,
            postfix = "\nMake sure to correctly chain all calls."
        ))
    }

    // Keep the list of statically provided artifact type since we register one task for all
    // static files of that artifact type.
    val staticProviders = mutableMapOf<Artifact<*>, TaskProvider<*>>()

    /**
     * add a static file/directory to this [ArtifactContainer]
     *
     * @param item the static file/directory to add.
     */
    internal fun <T: FileSystemLocation> addStaticProvider(
        container: MultipleArtifactContainer<T>,
        type: Artifact<T>,
        item: T,
    ) {
        // if no one is producing T yet, create an empty Task, register its output to be T
        // and register it to the providers for this artifact type.
        synchronized(staticProviders) {
            val taskProvider = staticProviders.getOrPut(type) {
                // register a unique task by combining the artifact type name and the variant name
                var taskName = "prepare$identifier${type.name()}StaticFile"
                taskContainer.register(taskName)
            }
            // whether the task just go registered or not, we need to add the new static file or
            // directory to its output.
            taskProvider.configure { task ->
                task.outputs.file(item)
            }

            val mappedValue = taskProvider.map { _ ->
                item
            }
            container.addInitialProvider(taskProvider, mappedValue)
        }
    }

    /**
     * add a static file/directory to this [ArtifactContainer]
     *
     * @param item the static file/directory to add.
     */
    internal fun <T: FileSystemLocation> addStaticProvider(
        container: MultipleArtifactContainer<T>,
        type: Artifact<T>,
        item: Provider<T>,
    ) {
        // if no one is producing T yet, create an empty Task, register its output to be T
        // and register it to the providers for this artifact type.
        synchronized(staticProviders) {
            val taskProvider = staticProviders.getOrPut(type) {
                // register a unique task by combining the artifact type name and the variant name
                var taskName = "prepare$identifier${type.name()}StaticFile"
                taskContainer.register(taskName)
            }
            // whether the task just go registered or not, we need to add the new static file or
            // directory to its output.
            taskProvider.configure { task ->
                task.outputs.file(item)
            }

            val mappedValue = taskProvider.flatMap { _ ->
                item
            }
            container.addInitialProvider(taskProvider, item)
        }
    }
}

/**
 * Function fromProperty returns an output property, so we can set path to output file.
 * Function fromProvider returns output provider that gradle uses to connect tasks together.
 * In most cases fromProperty is fromProvider, but sometimes (as for BundleAar/AbstractArchiveTask)
 * task has a provider declared as @Output. Gradle requires us to use the same provider that marked
 * as @Output to connect tasks. In this case we need separate mapped property to set output path for.
 */
internal class SingleInitialProviderRequestImpl<TASK: Task, FILE_TYPE: FileSystemLocation>(
    private val artifactsImpl: ArtifactsImpl,
    private val taskProvider: TaskProvider<TASK>,
    private val fromProperty: (TASK) -> FileSystemLocationProperty<FILE_TYPE>,
    private val fromProvider: ((TASK) -> Provider<FILE_TYPE>) = fromProperty
) {

    private var fileName: Property<String> = artifactsImpl.objects.property(String::class.java)
    private var buildOutputLocation: DirectoryProperty = artifactsImpl.objects.directoryProperty()

    private var absoluteOutputLocation: String? =  null

    /**
     * Internal API to set the location of the directory where the produced [FILE_TYPE] should
     * be located in.
     *
     * @param location a directory absolute path
     */
    fun atLocation(location: String?): SingleInitialProviderRequestImpl<TASK, FILE_TYPE> {
        absoluteOutputLocation = location
        return this
    }

    fun atLocation(
        finalLocation: Provider<Directory>
    ): SingleInitialProviderRequestImpl<TASK, FILE_TYPE> {
        buildOutputLocation.set(finalLocation)
        return this
    }

    fun atLocation(
        finalLocation: File
    ): SingleInitialProviderRequestImpl<TASK, FILE_TYPE> {
        buildOutputLocation.set(finalLocation)
        return this
    }

    fun withName(nameProperty: Provider<String>): SingleInitialProviderRequestImpl<TASK, FILE_TYPE> {
        fileName.set(nameProperty)
        return this
    }

    fun withName(name: String): SingleInitialProviderRequestImpl<TASK, FILE_TYPE> {
        fileName.set(name)
        return this
    }

    fun on(type: Single<FILE_TYPE>) {
        val artifactContainer = artifactsImpl.getArtifactContainer(type)

        // Need to store naming context to artifact container as there can be transformers
        // that will place transformed artifacts in exact same place.
        val context = ArtifactNamingContext(
            fileName,
            absoluteOutputLocation,
            buildOutputLocation
        )
        artifactContainer.initArtifactContainer(
            taskProvider, taskProvider.flatMap { fromProvider(it) }, context
        )

        taskProvider.configure {
            val outputAbsolutePath = artifactsImpl.calculateOutputPath(type, it)
            // since the taskProvider will execute, resolve its output path.
            fromProperty(it).set(outputAbsolutePath)
        }
    }
}

const val DEFAULT_FILE_NAME_OF_REGULAR_FILE_ARTIFACTS = "out.jar"
