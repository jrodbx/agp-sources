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
import com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES
import com.android.build.api.transform.QualifiedContent.Scope.EXTERNAL_LIBRARIES
import com.android.build.api.transform.QualifiedContent.Scope.PROJECT
import com.android.build.api.transform.QualifiedContent.Scope.SUB_PROJECTS
import com.android.build.api.transform.QualifiedContent.ScopeType
import com.android.build.gradle.internal.InternalScope.FEATURES
import com.android.build.gradle.internal.InternalScope.LOCAL_DEPS
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.pipeline.StreamFilter.PROJECT_RESOURCES
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVA_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.RUNTIME_R_CLASS_CLASSES
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.ide.common.resources.FileStatus
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
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
import org.gradle.work.DisableCachingByDefault

/**
 * Task to merge java resources from multiple modules
 */
@DisableCachingByDefault
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

    @get:Input
    abstract val excludes: SetProperty<String>

    @get:Input
    abstract val pickFirsts: SetProperty<String>

    @get:Input
    abstract val merges: SetProperty<String>

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
        workerExecutor.noIsolation().submit(MergeJavaResWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.projectJavaRes.from(unfilteredProjectJavaRes)
            it.subProjectJavaRes.from(subProjectJavaRes)
            it.externalLibJavaRes.from(externalLibJavaRes)
            it.featureJavaRes.from(featureJavaRes)
            it.outputFile.set(outputFile)
            it.incrementalStateFile.set(incrementalStateFile)
            it.incremental.set(false)
            it.cacheDir.set(cacheDir)
            it.noCompress.set(noCompress)
            it.excludes.set(excludes)
            it.pickFirsts.set(pickFirsts)
            it.merges.set(merges)
        }
    }

    override fun doIncrementalTaskAction(changedInputs: Map<File, FileStatus>) {
        if (!incrementalStateFile.isFile) {
            doFullTaskAction()
            return
        }
        workerExecutor.noIsolation().submit(MergeJavaResWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.projectJavaRes.from(unfilteredProjectJavaRes)
            it.subProjectJavaRes.from(subProjectJavaRes)
            it.externalLibJavaRes.from(externalLibJavaRes)
            it.featureJavaRes.from(featureJavaRes)
            it.outputFile.set(outputFile)
            it.incrementalStateFile.set(incrementalStateFile)
            it.incremental.set(true)
            it.cacheDir.set(cacheDir)
            it.changedInputs.set(changedInputs)
            it.noCompress.set(noCompress)
            it.excludes.set(excludes)
            it.pickFirsts.set(pickFirsts)
            it.merges.set(merges)
        }
    }

    class CreationAction(
        private val mergeScopes: Collection<ScopeType>,
        creationConfig: VariantCreationConfig
    ) : VariantTaskCreationAction<MergeJavaResourceTask, VariantCreationConfig>(
        creationConfig
    ) {

        private val projectJavaResFromStreams: FileCollection?

        override val name: String
            get() = computeTaskName("merge", "JavaResource")

        override val type: Class<MergeJavaResourceTask>
            get() = MergeJavaResourceTask::class.java

        init {
            if (creationConfig.variantScope.needsJavaResStreams) {
                // Because ordering matters for Transform pipeline, we need to fetch the java res
                // as soon as this creation action is instantiated, if needed.
                projectJavaResFromStreams =
                    creationConfig.transformManager
                        .getPipelineOutputAsFileCollection(PROJECT_RESOURCES)
                // We must also consume corresponding streams to avoid duplicates; any downstream
                // transforms will use the merged-java-res stream instead.
                creationConfig.transformManager
                    .consumeStreams(mutableSetOf(PROJECT), setOf(RESOURCES))
            } else {
                projectJavaResFromStreams = null
            }
        }

        override fun handleProvider(
            taskProvider: TaskProvider<MergeJavaResourceTask>
        ) {
            super.handleProvider(taskProvider)
            val fileName = if (creationConfig.variantType.isBaseModule) {
                "base.jar"
            } else {
                TaskManager.getFeatureFileName(
                    creationConfig.services.projectInfo.getProject().path,
                    SdkConstants.DOT_JAR
                )
            }
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                MergeJavaResourceTask::outputFile
            ).withName(fileName).on(InternalArtifactType.MERGED_JAVA_RES)
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
            task.excludes.setDisallowChanges(creationConfig.packaging.resources.excludes)
            task.pickFirsts.setDisallowChanges(creationConfig.packaging.resources.pickFirsts)
            task.merges.setDisallowChanges(creationConfig.packaging.resources.merges)
            task.intermediateDir =
                creationConfig.paths.getIncrementalDir("${creationConfig.name}-mergeJavaRes")
            task.cacheDir = File(task.intermediateDir, "zip-cache")
            task.incrementalStateFile = File(task.intermediateDir, "merge-state")
            if (creationConfig is ApkCreationConfig) {
                task.noCompress.set(creationConfig.services.projectInfo.getExtension().aaptOptions.noCompress)
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
    creationConfig: ComponentCreationConfig
): FileCollection {
    val javaRes = creationConfig.services.fileCollection()
    javaRes.from(creationConfig.artifacts.get(JAVA_RES))
    // use lazy file collection here in case an annotationProcessor dependency is add via
    // Configuration.defaultDependencies(), for example.
    javaRes.from(
        Callable {
            if (projectHasAnnotationProcessors(creationConfig)) {
                creationConfig.artifacts.get(JAVAC)
            } else {
                listOf<File>()
            }
        }
    )
    javaRes.from(creationConfig.variantData.allPreJavacGeneratedBytecode)
    javaRes.from(creationConfig.variantData.allPostJavacGeneratedBytecode)
    if (creationConfig.services.projectInfo.getExtension().aaptOptions.namespaced) {
        javaRes.from(creationConfig.artifacts.get(RUNTIME_R_CLASS_CLASSES))
    }
    if (creationConfig.packageJacocoRuntime) {
        javaRes.from(creationConfig.artifacts.get(InternalArtifactType.JACOCO_CONFIG_RESOURCES_JAR))
    }
    return javaRes
}

private fun getExternalLibJavaRes(
    creationConfig: VariantCreationConfig,
    mergeScopes: Collection<ScopeType>
): FileCollection {
    val externalLibJavaRes = creationConfig.services.fileCollection()
    if (mergeScopes.contains(EXTERNAL_LIBRARIES)) {
        externalLibJavaRes.from(
            creationConfig.variantDependencies.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                AndroidArtifacts.ArtifactScope.EXTERNAL,
                AndroidArtifacts.ArtifactType.JAVA_RES
            )
        )
    }
    if (mergeScopes.contains(LOCAL_DEPS)) {
        externalLibJavaRes.from(creationConfig.variantScope.localPackagedJars)
    }
    return externalLibJavaRes
}

/** Returns true if anything's been added to the annotation processor configuration. */
fun projectHasAnnotationProcessors(creationConfig: ComponentCreationConfig): Boolean {
    val config = creationConfig.variantDependencies.annotationProcessorConfiguration
    return config.incoming.dependencies.isNotEmpty()
}
