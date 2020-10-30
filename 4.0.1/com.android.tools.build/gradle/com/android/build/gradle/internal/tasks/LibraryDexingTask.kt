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

import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.MultipleArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.Workers.preferWorkers
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.getDesugarLibConfig
import com.android.build.gradle.options.SyncOptions
import com.android.builder.dexing.ClassFileInputs
import com.android.builder.dexing.DexArchiveBuilder
import com.android.builder.dexing.DexParameters
import com.android.builder.dexing.r8.ClassFileProviderFactory
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import com.google.common.util.concurrent.MoreExecutors
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.Serializable
import javax.inject.Inject

/**
 * This is implementation of dexing artifact transform as a task. It is used when building
 * android test variant for library projects. Once http://b/115334911 is fixed, this can be removed.
 */
@CacheableTask
abstract class LibraryDexingTask : NonIncrementalTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classes: RegularFileProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @get:Input
    var minSdkVersion = 1
        private set

    @get:Input
    abstract val enableDesugaring: Property<Boolean>

    @get:Classpath
    abstract val bootClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    @get:Internal
    lateinit var errorFormatMode: SyncOptions.ErrorFormatMode
        private set

    @get:Optional
    @get:Input
    abstract val coreLibDesugarConfig: Property<String>

    @get:Optional
    @get:OutputDirectory
    abstract val coreLibDesugarKeepRules: DirectoryProperty

    override fun doTaskAction() {
        preferWorkers(
            projectName,
            path,
            workerExecutor,
            enableGradleWorkers.get(),
            MoreExecutors.newDirectExecutorService()
        ).use {
            val coreLibDesugarKeepRules =
                coreLibDesugarKeepRules.asFile.orNull?.resolve("keepRules")?.also { file ->
                    FileUtils.mkdirs(file.parentFile)
                    file.createNewFile()
                }
            it.submit(
                DexingRunnable::class.java,
                DexParams(
                    minSdkVersion,
                    errorFormatMode,
                    classes.get().asFile,
                    output.get().asFile,
                    enableDesugaring = enableDesugaring.get(),
                    bootClasspath = bootClasspath.files,
                    classpath = classpath.files,
                    coreLibDesugarConfig = coreLibDesugarConfig.orNull,
                    coreLibDesugarKeepRules = coreLibDesugarKeepRules
                )
            )
        }
    }

    class CreationAction(val scope: VariantScope) :
        VariantTaskCreationAction<LibraryDexingTask>(scope) {
        override val name = scope.getTaskName("dex")
        override val type = LibraryDexingTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out LibraryDexingTask>) {
            super.handleProvider(taskProvider)
            scope.artifacts.getOperations().append(
                taskProvider,
                LibraryDexingTask::output
            ).on(MultipleArtifactType.DEX)

            if (variantScope.needsShrinkDesugarLibrary) {
                variantScope.artifacts.getOperations()
                    .setInitialProvider(taskProvider, LibraryDexingTask::coreLibDesugarKeepRules)
                    .on(InternalArtifactType.DESUGAR_LIB_PROJECT_KEEP_RULES)
            }
        }

        override fun configure(task: LibraryDexingTask) {
            super.configure(task)
            scope.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.RUNTIME_LIBRARY_CLASSES_JAR,
                task.classes
            )
            val minSdkVersion =
                scope.variantDslInfo.minSdkVersionWithTargetDeviceApi.featureLevel
            task.minSdkVersion = minSdkVersion
            task.errorFormatMode =
                SyncOptions.getErrorFormatMode(variantScope.globalScope.projectOptions)
            // This is consumed only by library androidTest so desugar whenever java 8 lang
            // support is enabled.
            if (scope.java8LangSupportType != VariantScope.Java8LangSupport.INVALID
                && scope.java8LangSupportType != VariantScope.Java8LangSupport.UNUSED
            ) {
                task.enableDesugaring.set(true)

                if (minSdkVersion < AndroidVersion.VersionCodes.N) {
                    task.bootClasspath.from(scope.globalScope.bootClasspath)
                    task.classpath.from(
                        scope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.CLASSES_JAR
                        )
                    )
                }
            } else {
                task.enableDesugaring.set(false)
            }
            if (scope.isCoreLibraryDesugaringEnabled) {
                task.coreLibDesugarConfig.set(getDesugarLibConfig(scope.globalScope.project))
            }
        }
    }
}

private class DexParams(
    val minSdkVersion: Int,
    val errorFormatMode: SyncOptions.ErrorFormatMode,
    val input: File,
    val output: File,
    val enableDesugaring: Boolean,
    val bootClasspath: Collection<File>,
    val classpath: Collection<File>,
    val coreLibDesugarConfig: String?,
    val coreLibDesugarKeepRules: File?
) : Serializable

private class DexingRunnable @Inject constructor(val params: DexParams) : Runnable {
    override fun run() {
        ClassFileProviderFactory(params.bootClasspath.map(File::toPath)).use { bootClasspath ->
            ClassFileProviderFactory(params.classpath.map(File::toPath)).use { classpath ->
                val d8DexBuilder = DexArchiveBuilder.createD8DexBuilder(
                    DexParameters(
                        minSdkVersion = params.minSdkVersion,
                        debuggable = true,
                        dexPerClass = false,
                        withDesugaring = params.enableDesugaring,
                        desugarBootclasspath = bootClasspath,
                        desugarClasspath = classpath,
                        coreLibDesugarConfig = params.coreLibDesugarConfig,
                        coreLibDesugarOutputKeepRuleFile = params.coreLibDesugarKeepRules,
                        messageReceiver = MessageReceiverImpl(
                            params.errorFormatMode,
                            Logging.getLogger(LibraryDexingTask::class.java)
                        )
                    )
                )

                ClassFileInputs.fromPath(params.input.toPath()).use { classFileInput ->
                    classFileInput.entries { _, _ -> true }.use { classesInput ->
                        d8DexBuilder.convert(
                            classesInput,
                            params.output.toPath()
                        )
                    }
                }
            }
        }
    }
}