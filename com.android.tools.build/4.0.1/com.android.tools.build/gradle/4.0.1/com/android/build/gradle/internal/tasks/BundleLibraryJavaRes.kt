/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants
import com.android.SdkConstants.FN_INTERMEDIATE_RES_JAR
import com.android.build.gradle.internal.packaging.JarCreatorFactory
import com.android.build.gradle.internal.packaging.JarCreatorType
import com.android.build.gradle.internal.pipeline.StreamFilter.PROJECT_RESOURCES
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.Serializable
import java.nio.file.Files
import java.util.function.Predicate
import java.util.zip.Deflater
import javax.inject.Inject

/** Bundle all library Java resources in a jar.  */
@CacheableTask
abstract class BundleLibraryJavaRes : NonIncrementalTask() {

    @get:OutputFile
    abstract val output: RegularFileProperty

    // We cannot use @Classpath as it ignores empty directories which may be used as Java resources.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:SkipWhenEmpty
    @get:Optional
    var resources: FileCollection? = null
        private set

    // We cannot use @Classpath as it ignores empty directories which may be used as Java resources.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:SkipWhenEmpty
    @get:Optional
    var resourcesAsJars: FileCollection? = null
        private set

    @get:Input
    lateinit var jarCreatorType: JarCreatorType
        private set

    @get:Input
    abstract val debuggable: Property<Boolean>

    // The runnable implementing the processing is not able to deal with fine-grained file but
    // instead is expecting directories of files. Use the unfiltered collection (since the filtering
    // changes the FileCollection of directories into a FileTree of files) to process, but don't
    // use it as a jar input, it's covered by the two items above.
    private lateinit var unfilteredResources: FileCollection

    override fun doTaskAction() {
        getWorkerFacadeWithWorkers().use {
            it.submit(
                BundleLibraryJavaResRunnable::class.java,
                BundleLibraryJavaResRunnable.Params(
                    output = output!!.get().asFile,
                    inputs = unfilteredResources.files,
                    jarCreatorType = jarCreatorType,
                    compressionLevel = if (debuggable.get()) Deflater.BEST_SPEED else null
                )
            )
        }
    }

    class CreationAction(scope: VariantScope) :
        VariantTaskCreationAction<BundleLibraryJavaRes>(scope) {

        private val projectJavaResFromStreams = if (variantScope.needsJavaResStreams) {
            // Because ordering matters for TransformAPI, we need to fetch java res from the
            // transform pipeline as soon as this creation action is instantiated, in needed.
            variantScope.transformManager.getPipelineOutputAsFileCollection(PROJECT_RESOURCES)
        } else {
            null
        }

        override val name: String = scope.getTaskName("bundleLibRes")

        override val type: Class<BundleLibraryJavaRes> = BundleLibraryJavaRes::class.java

        override fun handleProvider(taskProvider: TaskProvider<out BundleLibraryJavaRes>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesFile(
                InternalArtifactType.LIBRARY_JAVA_RES,
                taskProvider,
                BundleLibraryJavaRes::output,
                FN_INTERMEDIATE_RES_JAR
            )
        }

        override fun configure(task: BundleLibraryJavaRes) {
            super.configure(task)

            // we should have two tasks with each input and ensure that only one runs for any build.
            if (projectJavaResFromStreams != null) {
                task.resourcesAsJars = projectJavaResFromStreams
                task.unfilteredResources = projectJavaResFromStreams
            } else {
                val projectJavaRes = getProjectJavaRes(variantScope)
                task.unfilteredResources = projectJavaRes
                task.resources = projectJavaRes.asFileTree.filter(MergeJavaResourceTask.spec)
            }

            task.jarCreatorType = variantScope.jarCreatorType
            task.debuggable
                .setDisallowChanges(variantScope.variantDslInfo.isDebuggable)
        }
    }
}

class BundleLibraryJavaResRunnable @Inject constructor(val params: Params) : Runnable {
    data class Params(
        val output: File,
        val inputs: Set<File>,
        val jarCreatorType: JarCreatorType,
        val compressionLevel: Int?
    ) : Serializable

    override fun run() {
        Files.deleteIfExists(params.output.toPath())
        params.output.parentFile.mkdirs()

        val predicate = Predicate<String> { entry -> !entry.endsWith(SdkConstants.DOT_CLASS) }
        JarCreatorFactory.make(
            params.output.toPath(),
            predicate,
            params.jarCreatorType
        ).use { jarCreator ->
            params.compressionLevel?.let { jarCreator.setCompressionLevel(it) }
            params.inputs.forEach { base ->
                if (base.isDirectory) {
                    jarCreator.addDirectory(base.toPath())
                } else if (base.isFile && base.name.endsWith(SdkConstants.DOT_JAR)) {
                    jarCreator.addJar(base.toPath())
                }
            }
        }
    }
}