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
import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES
import com.android.build.api.transform.QualifiedContent.Scope.EXTERNAL_LIBRARIES
import com.android.build.api.transform.QualifiedContent.Scope.PROJECT
import com.android.build.api.transform.QualifiedContent.Scope.SUB_PROJECTS
import com.android.build.api.transform.QualifiedContent.ScopeType
import com.android.build.gradle.internal.InternalScope.FEATURES
import com.android.build.gradle.internal.InternalScope.LOCAL_DEPS
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.packaging.SerializablePackagingOptions
import com.android.build.gradle.internal.pipeline.StreamFilter.PROJECT_RESOURCES
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVA_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.RUNTIME_R_CLASS_CLASSES
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.ide.common.resources.FileStatus
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.util.PatternSet
import java.io.File
import java.util.concurrent.Callable
import java.util.function.Predicate
import javax.inject.Inject

/**
 * Task to merge java resources from multiple modules
 */
@CacheableTask
abstract class MergeJavaResourceTask
@Inject constructor(objects: ObjectFactory) : IncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val projectJavaRes: ConfigurableFileCollection

    @get:Classpath
    @get:Optional
    var projectJavaResAsJars: FileCollection? = null
        private set

    @get:Classpath
    @get:Optional
    var subProjectJavaRes: FileCollection? = null
        private set

    @get:Classpath
    @get:Optional
    var externalLibJavaRes: FileCollection? = null
        private set

    @get:Classpath
    @get:Optional
    var featureJavaRes: FileCollection? = null
        private set

    @get:Input
    lateinit var mergeScopes: Collection<ScopeType>
        private set

    @get:Nested
    lateinit var packagingOptions: SerializablePackagingOptions
        private set

    @get:Input
    @get:Optional
    abstract val noCompress: ListProperty<String>

    private lateinit var intermediateDir: File

    @get:OutputDirectory
    lateinit var cacheDir: File
        private set

    private lateinit var incrementalStateFile: File

    @get:OutputFile
    val outputFile: RegularFileProperty = objects.fileProperty()

    override val incremental: Boolean
        get() = true

    // The runnable implementing the processing is not able to deal with fine-grained file but
    // instead is expecting directories of files. Use the unfiltered collection (since the filtering
    // changes the FileCollection of directories into a FileTree of files) to process, but don't
    // use it as a task input, it's covered by [projectJavaRes] and [projectJavaResAsJars] above.
    // This is a workaround for the lack of gradle custom snapshotting:
    // https://github.com/gradle/gradle/issues/8503.
    private lateinit var unfilteredProjectJavaRes: FileCollection

    override fun doFullTaskAction() {
        getWorkerFacadeWithWorkers().use {
            it.submit(
                MergeJavaResRunnable::class.java,
                MergeJavaResRunnable.Params(
                    unfilteredProjectJavaRes.files,
                    subProjectJavaRes?.files,
                    externalLibJavaRes?.files,
                    featureJavaRes?.files,
                    outputFile.get().asFile,
                    packagingOptions,
                    incrementalStateFile,
                    false,
                    cacheDir,
                    null,
                    RESOURCES,
                    noCompress.orNull ?: listOf()
                )
            )
        }
    }

    override fun doIncrementalTaskAction(changedInputs: Map<File, FileStatus>) {
        if (!incrementalStateFile.isFile) {
            doFullTaskAction()
            return
        }
        getWorkerFacadeWithWorkers().use {
            it.submit(
                MergeJavaResRunnable::class.java,
                MergeJavaResRunnable.Params(
                    unfilteredProjectJavaRes.files,
                    subProjectJavaRes?.files,
                    externalLibJavaRes?.files,
                    featureJavaRes?.files,
                    outputFile.get().asFile,
                    packagingOptions,
                    incrementalStateFile,
                    true,
                    cacheDir,
                    changedInputs,
                    RESOURCES,
                    noCompress.orNull ?: listOf()
                )
            )
        }
    }

    class CreationAction(
        private val mergeScopes: Collection<ScopeType>,
        componentProperties: ComponentPropertiesImpl
    ) : VariantTaskCreationAction<MergeJavaResourceTask, ComponentPropertiesImpl>(
        componentProperties
    ) {

        private val projectJavaResFromStreams: FileCollection?

        override val name: String
            get() = computeTaskName("merge", "JavaResource")

        override val type: Class<MergeJavaResourceTask>
            get() = MergeJavaResourceTask::class.java

        init {
            if (componentProperties.variantScope.needsJavaResStreams) {
                // Because ordering matters for Transform pipeline, we need to fetch the java res
                // as soon as this creation action is instantiated, if needed.
                projectJavaResFromStreams =
                    componentProperties.transformManager
                        .getPipelineOutputAsFileCollection(PROJECT_RESOURCES)
                // We must also consume corresponding streams to avoid duplicates; any downstream
                // transforms will use the merged-java-res stream instead.
                componentProperties.transformManager
                    .consumeStreams(mutableSetOf(PROJECT), setOf(RESOURCES))
            } else {
                projectJavaResFromStreams = null
            }
        }

        override fun handleProvider(
            taskProvider: TaskProvider<MergeJavaResourceTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                MergeJavaResourceTask::outputFile
            ).withName("out.jar").on(InternalArtifactType.MERGED_JAVA_RES)
        }

        override fun configure(
            task: MergeJavaResourceTask
        ) {
            super.configure(task)

            if (projectJavaResFromStreams != null) {
                task.projectJavaResAsJars = projectJavaResFromStreams
                task.unfilteredProjectJavaRes = projectJavaResFromStreams
            } else {
                val projectJavaRes = getProjectJavaRes(creationConfig)
                task.unfilteredProjectJavaRes = projectJavaRes
                task.projectJavaRes.from(projectJavaRes.asFileTree.matching(patternSet))
            }
            task.projectJavaRes.disallowChanges()

            if (mergeScopes.contains(SUB_PROJECTS)) {
                task.subProjectJavaRes =
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        AndroidArtifacts.ArtifactType.JAVA_RES
                    )
            }

            if (mergeScopes.contains(EXTERNAL_LIBRARIES) || mergeScopes.contains(LOCAL_DEPS)) {
                // Local jars are treated the same as external libraries
                task.externalLibJavaRes = getExternalLibJavaRes(creationConfig, mergeScopes)
            }

            if (mergeScopes.contains(FEATURES)) {
                task.featureJavaRes =
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        AndroidArtifacts.ArtifactType.REVERSE_METADATA_JAVA_RES
                    )
            }

            task.mergeScopes = mergeScopes
            task.packagingOptions =
                SerializablePackagingOptions(
                    creationConfig.globalScope.extension.packagingOptions
                )
            task.intermediateDir =
                creationConfig.paths.getIncrementalDir("${creationConfig.name}-mergeJavaRes")
            task.cacheDir = File(task.intermediateDir, "zip-cache")
            task.incrementalStateFile = File(task.intermediateDir, "merge-state")
            if (creationConfig is ApkCreationConfig) {
                task.noCompress.set(creationConfig.globalScope.extension.aaptOptions.noCompress)
            }
            task.noCompress.disallowChanges()
        }
    }

    companion object {

        private val excludedFileSuffixes =
            listOf(SdkConstants.DOT_CLASS, SdkConstants.DOT_NATIVE_LIBS)

        // predicate logic must match patternSet logic below
        val predicate = Predicate<String> { path ->
            excludedFileSuffixes.none { path.endsWith(it) }
        }

        // patternSet logic must match predicate logic above
        val patternSet: PatternSet
            get() {
                val patternSet = PatternSet()
                excludedFileSuffixes.forEach { patternSet.exclude("**/*$it") }
                return patternSet
            }
    }
}

fun getProjectJavaRes(
    componentProperties: ComponentPropertiesImpl
): FileCollection {
    val javaRes = componentProperties.globalScope.project.files()
    javaRes.from(componentProperties.artifacts.get(JAVA_RES))
    // use lazy file collection here in case an annotationProcessor dependency is add via
    // Configuration.defaultDependencies(), for example.
    javaRes.from(
        Callable {
            if (projectHasAnnotationProcessors(componentProperties)) {
                componentProperties.artifacts.get(JAVAC)
            } else {
                listOf<File>()
            }
        }
    )
    javaRes.from(componentProperties.variantData.allPreJavacGeneratedBytecode)
    javaRes.from(componentProperties.variantData.allPostJavacGeneratedBytecode)
    if (componentProperties.globalScope.extension.aaptOptions.namespaced) {
        javaRes.from(componentProperties.artifacts.get(RUNTIME_R_CLASS_CLASSES))
    }
    return javaRes
}

private fun getExternalLibJavaRes(
    componentProperties: ComponentPropertiesImpl,
    mergeScopes: Collection<ScopeType>
): FileCollection {
    val externalLibJavaRes = componentProperties.globalScope.project.files()
    if (mergeScopes.contains(EXTERNAL_LIBRARIES)) {
        externalLibJavaRes.from(
            componentProperties.variantDependencies.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                AndroidArtifacts.ArtifactScope.EXTERNAL,
                AndroidArtifacts.ArtifactType.JAVA_RES
            )
        )
    }
    if (mergeScopes.contains(LOCAL_DEPS)) {
        externalLibJavaRes.from(componentProperties.variantScope.localPackagedJars)
    }
    return externalLibJavaRes
}

/** Returns true if anything's been added to the annotation processor configuration. */
fun projectHasAnnotationProcessors(componentProperties: ComponentPropertiesImpl): Boolean {
    val config = componentProperties.variantDependencies.annotationProcessorConfiguration
    return config.incoming.dependencies.isNotEmpty()
}
