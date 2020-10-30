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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.getDesugarLibConfig
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.SyncOptions
import com.android.builder.dexing.ClassFileInputs
import com.android.builder.dexing.DexArchiveBuilder
import com.android.builder.dexing.DexParameters
import com.android.builder.dexing.r8.ClassFileProviderFactory
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import com.google.common.io.Closer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.Serializable
import javax.inject.Inject

abstract class DexFileDependenciesTask: NonIncrementalTask() {

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Optional
    @get:OutputFile
    abstract val outputKeepRules: RegularFileProperty

    @get:Classpath
    abstract val classes: ConfigurableFileCollection

    @get:CompileClasspath
    abstract val classpath: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val bootClasspath: ConfigurableFileCollection

    @get:Input
    abstract val minSdkVersion: Property<Int>

    @get:Input
    abstract val debuggable: Property<Boolean>

    @get:Optional
    @get:Input
    abstract val libConfiguration: Property<String>

    private lateinit var errorFormatMode: SyncOptions.ErrorFormatMode

    // TODO: make incremental
    override fun doTaskAction() {
        getWorkerFacadeWithWorkers().use { workerExecutorFacade ->
            val inputs = classes.files.toList()
            val totalClasspath = inputs + classpath.files
            val outDir = outputDirectory.get().asFile
            inputs.forEachIndexed { index, input ->
                // Desugar each jar with reference to all the others
                workerExecutorFacade.submit(
                    DexFileDependenciesWorkerAction::class.java,
                    DexFileDependenciesWorkerActionParams(
                        minSdkVersion = minSdkVersion.get(),
                        debuggable = debuggable.get(),
                        bootClasspath = bootClasspath.files,
                        classpath = totalClasspath,
                        input = input,
                        outputFile = outDir.resolve("${index}_${input.name}"),
                        errorFormatMode = errorFormatMode,
                        libConfiguration = libConfiguration.orNull,
                        outputKeepRules = outputKeepRules.asFile.orNull
                    )
                )
            }
        }
    }

    data class DexFileDependenciesWorkerActionParams(
        val minSdkVersion: Int,
        val debuggable: Boolean,
        val bootClasspath: Collection<File>,
        val classpath: Collection<File>,
        val input: File,
        val outputFile: File,
        val errorFormatMode: SyncOptions.ErrorFormatMode,
        val libConfiguration: String?,
        val outputKeepRules: File?
    ) : Serializable

    class DexFileDependenciesWorkerAction @Inject constructor(private val params: DexFileDependenciesWorkerActionParams) :
        Runnable {

        override fun run() {
            val bootClasspath = params.bootClasspath.map(File::toPath)
            val classpath = params.classpath.map(File::toPath)
            Closer.create().use { closer ->
                val d8DexBuilder = DexArchiveBuilder.createD8DexBuilder(
                    DexParameters(
                        minSdkVersion = params.minSdkVersion,
                        debuggable = params.debuggable,
                        dexPerClass = false,
                        withDesugaring = true,
                        desugarBootclasspath = ClassFileProviderFactory(bootClasspath).also {
                            closer.register(it)
                        },
                        desugarClasspath = ClassFileProviderFactory(classpath).also {
                            closer.register(it)
                        },
                        coreLibDesugarConfig = params.libConfiguration,
                        coreLibDesugarOutputKeepRuleFile = params.outputKeepRules,
                        messageReceiver = MessageReceiverImpl(
                            errorFormatMode = params.errorFormatMode,
                            logger = Logging.getLogger(DexFileDependenciesWorkerAction::class.java)
                        )
                    )
                )


                ClassFileInputs.fromPath(params.input.toPath()).use { classFileInput ->
                    classFileInput.entries { _, _ -> true }.use { classesInput ->
                        d8DexBuilder.convert(
                            classesInput,
                            params.outputFile.toPath()
                        )
                    }
                }
            }
        }
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<DexFileDependenciesTask>(variantScope) {
        override val name: String = variantScope.getTaskName("desugar", "FileDependencies")
        override val type = DexFileDependenciesTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out DexFileDependenciesTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesDir(
                artifactType = InternalArtifactType.EXTERNAL_FILE_LIB_DEX_ARCHIVES,
                taskProvider = taskProvider,
                productProvider = DexFileDependenciesTask::outputDirectory,
                fileName = "out"
            )

            if (variantScope.needsShrinkDesugarLibrary) {
                variantScope.artifacts.getOperations()
                    .setInitialProvider(taskProvider, DexFileDependenciesTask::outputKeepRules)
                    .on(InternalArtifactType.DESUGAR_LIB_EXTERNAL_FILE_LIB_KEEP_RULES)
            }
        }

        override fun configure(task: DexFileDependenciesTask) {
            super.configure(task)
            task.debuggable
                .setDisallowChanges(variantScope.variantDslInfo.isDebuggable)
            task.classes.from(
                variantScope.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.FILE,
                    AndroidArtifacts.ArtifactType.PROCESSED_JAR
                )
            ).disallowChanges()
            val minSdkVersion =
                variantScope.variantDslInfo.minSdkVersionWithTargetDeviceApi.featureLevel
            task.minSdkVersion.setDisallowChanges(minSdkVersion)
            if (minSdkVersion < AndroidVersion.VersionCodes.N) {
                task.classpath.from(
                    variantScope.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.REPOSITORY_MODULE,
                        AndroidArtifacts.ArtifactType.PROCESSED_JAR
                    )
                )
                task.bootClasspath.from(variantScope.globalScope.bootClasspath)
            }

            task.classpath.disallowChanges()
            task.bootClasspath.disallowChanges()

            task.errorFormatMode =
                SyncOptions.getErrorFormatMode(variantScope.globalScope.projectOptions)

            if (variantScope.isCoreLibraryDesugaringEnabled) {
                task.libConfiguration.set(getDesugarLibConfig(variantScope.globalScope.project))
            }
            task.libConfiguration.disallowChanges()
        }
    }
}