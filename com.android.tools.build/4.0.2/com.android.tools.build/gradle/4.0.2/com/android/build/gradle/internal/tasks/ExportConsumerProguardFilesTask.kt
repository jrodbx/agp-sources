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

import com.android.SdkConstants
import com.android.build.gradle.ProguardFiles
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.immutableMapBuilder
import com.android.builder.errors.EvalIssueException
import com.android.utils.FileUtils
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.Serializable
import java.util.function.Consumer
import javax.inject.Inject

@CacheableTask
abstract class ExportConsumerProguardFilesTask : NonIncrementalTask() {

    @get:Input
    var isBaseModule: Boolean = false
        private set
    @get:Input
    var isDynamicFeature: Boolean = false
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val consumerProguardFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    public override fun doTaskAction() {
        // We check for default files unless it's a base feature, which can include default files.
        if (!isBaseModule) {
            checkProguardFiles(
                project,
                isDynamicFeature,
                consumerProguardFiles.files,
                Consumer { exception -> throw EvalIssueException(exception) }
            )
        }
        getWorkerFacadeWithWorkers().use {
            it.submit(
                ExportConsumerProguardRunnable::class.java,
                ExportConsumerProguardRunnable.Params(
                    inputFiles.files,
                    outputDir.get().asFile
                )
            )
        }
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<ExportConsumerProguardFilesTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("export", "ConsumerProguardFiles")

        override val type: Class<ExportConsumerProguardFilesTask>
            get() = ExportConsumerProguardFilesTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<out ExportConsumerProguardFilesTask>
        ) {
            super.handleProvider(taskProvider)

            variantScope.artifacts.producesDir(
                InternalArtifactType.CONSUMER_PROGUARD_DIR,
                taskProvider,
                ExportConsumerProguardFilesTask::outputDir,
                ""
            )
        }

        override fun configure(task: ExportConsumerProguardFilesTask) {
            super.configure(task)

            task.consumerProguardFiles.from(variantScope.consumerProguardFilesForFeatures)
            task.isBaseModule = variantScope.type.isBaseModule
            task.isDynamicFeature = variantScope.type.isDynamicFeature

            task.inputFiles.from(
                task.consumerProguardFiles,
                variantScope
                    .artifacts
                    .getFinalProduct(InternalArtifactType.GENERATED_PROGUARD_FILE)
            )
            if (variantScope.type.isDynamicFeature) {
                task.inputFiles.from(
                    variantScope.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.UNFILTERED_PROGUARD_RULES
                    )
                )
            }
        }
    }

    companion object {
        @JvmStatic
        fun checkProguardFiles(
            project: Project,
            isDynamicFeature: Boolean,
            consumerProguardFiles: Collection<File>,
            exceptionHandler: Consumer<String>
        ) {
            val defaultFiles = immutableMapBuilder<File, String> {
                for (knownFileName in ProguardFiles.KNOWN_FILE_NAMES) {
                    this.put(ProguardFiles.getDefaultProguardFile(knownFileName, project.layout),
                        knownFileName)
                }
            }

            for (consumerProguardFile in consumerProguardFiles) {
                if (defaultFiles.containsKey(consumerProguardFile)) {
                    val errorMessage = if (isDynamicFeature) {
                        ("Default file "
                                + defaultFiles[consumerProguardFile]
                                + " should not be specified in this module."
                                + " It can be specified in the base module instead.")

                    } else {
                        ("Default file "
                                + defaultFiles[consumerProguardFile]
                                + " should not be used as a consumer configuration file.")
                    }

                    exceptionHandler.accept(errorMessage)
                }
            }
        }
    }
}

private class ExportConsumerProguardRunnable @Inject constructor(private val params: Params) : Runnable {
    override fun run() {
        FileUtils.deleteRecursivelyIfExists(params.outputDir)
        var counter = 0
        params.input.forEach { input ->
            if (input.isFile) {
                val libSubDir = getLibSubDir(counter++)
                input.copyTo(File(libSubDir, SdkConstants.FN_PROGUARD_TXT))
            } else if (input.isDirectory) {
                input.listFiles { it -> it.isDirectory }?.forEach {
                    val libSubDir = getLibSubDir(counter++)
                    it.copyRecursively(libSubDir)
                }
            }
        }
    }

    private fun getLibSubDir(count: Int) = File(params.outputDir, "lib$count").also { it.mkdirs() }

    data class Params(
        val input: Set<File>,
        val outputDir: File
    ) : Serializable
}