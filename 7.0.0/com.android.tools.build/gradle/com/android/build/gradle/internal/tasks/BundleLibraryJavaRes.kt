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
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.packaging.JarCreatorFactory
import com.android.build.gradle.internal.packaging.JarCreatorType
import com.android.build.gradle.internal.pipeline.StreamFilter.PROJECT_RESOURCES
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.file.ConfigurableFileCollection
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
import java.nio.file.Files
import java.util.function.Predicate
import java.util.zip.Deflater

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
        workerExecutor.noIsolation().submit(BundleLibraryJavaResRunnable::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.output.set(output)
            it.inputs.from(unfilteredResources)
            it.jarCreatorType.set(jarCreatorType)
            it.compressionLevel.set(if (debuggable.get()) Deflater.BEST_SPEED else null)
        }
    }

    class CreationAction(
        creationConfig: ComponentCreationConfig
    ) : VariantTaskCreationAction<BundleLibraryJavaRes, ComponentCreationConfig>(
        creationConfig
    ) {

        private val projectJavaResFromStreams = if (creationConfig.variantScope.needsJavaResStreams) {
            // Because ordering matters for TransformAPI, we need to fetch java res from the
            // transform pipeline as soon as this creation action is instantiated, in needed.
            creationConfig.transformManager.getPipelineOutputAsFileCollection(PROJECT_RESOURCES)
        } else {
            null
        }

        override val name: String = computeTaskName("bundleLibRes")

        override val type: Class<BundleLibraryJavaRes> = BundleLibraryJavaRes::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<BundleLibraryJavaRes>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                BundleLibraryJavaRes::output
            ).withName(FN_INTERMEDIATE_RES_JAR).on(InternalArtifactType.LIBRARY_JAVA_RES)
        }

        override fun configure(
            task: BundleLibraryJavaRes
        ) {
            super.configure(task)

            // we should have two tasks with each input and ensure that only one runs for any build.
            if (projectJavaResFromStreams != null) {
                task.resourcesAsJars = projectJavaResFromStreams
                task.unfilteredResources = projectJavaResFromStreams
            } else {
                val projectJavaRes = getProjectJavaRes(creationConfig)
                task.unfilteredResources = projectJavaRes
                task.resources =
                    projectJavaRes.asFileTree.matching(MergeJavaResourceTask.patternSet)
            }

            task.jarCreatorType = creationConfig.variantScope.jarCreatorType
            task.debuggable
                .setDisallowChanges(creationConfig.debuggable)
        }
    }
}

abstract class BundleLibraryJavaResRunnable : ProfileAwareWorkAction<BundleLibraryJavaResRunnable.Params>() {
    abstract class Params : ProfileAwareWorkAction.Parameters() {
        abstract val output: RegularFileProperty
        abstract val inputs: ConfigurableFileCollection
        abstract val jarCreatorType: Property<JarCreatorType>
        abstract val compressionLevel: Property<Int>
    }

    override fun run() {
        with(parameters.output.asFile.get()) {
            Files.deleteIfExists(toPath())
            parentFile.mkdirs()
        }

        val predicate = Predicate<String> { entry -> !entry.endsWith(SdkConstants.DOT_CLASS) }
        JarCreatorFactory.make(
            parameters.output.asFile.get().toPath(),
            predicate,
            parameters.jarCreatorType.get()
        ).use { jarCreator ->
            parameters.compressionLevel.orNull?.let { jarCreator.setCompressionLevel(it) }
            parameters.inputs.forEach { base ->
                if (base.isDirectory) {
                    jarCreator.addDirectory(base.toPath())
                } else if (base.isFile && base.name.endsWith(SdkConstants.DOT_JAR)) {
                    jarCreator.addJar(base.toPath())
                }
            }
        }
    }
}
