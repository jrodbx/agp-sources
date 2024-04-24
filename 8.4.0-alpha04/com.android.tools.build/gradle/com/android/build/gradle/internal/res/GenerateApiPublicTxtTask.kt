/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.res

import com.android.build.api.artifact.SingleArtifact
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.symbols.writePublicTxtFile
import com.android.ide.common.symbols.SymbolIo
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files
import java.nio.file.Path

/**
 * Task to generate the public.txt API artifact [SingleArtifact.PUBLIC_ANDROID_RESOURCES_LIST]
 *
 * The artifact in the AAR has the challenging-for-consumers attribute (They can;t ) of sometimes not existing,
 * so this tasks
 *
 * Task to take the (possibly not existing) internal public API file and generate one that exists unconditionally
 *
 * Caching disabled by default for this task because the task does very little work.
 * Calculating cache hit/miss and fetching results is likely more expensive than
 *  simply executing the task.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.METADATA)
abstract class GenerateApiPublicTxtTask : NonIncrementalTask() {

    @get:InputFiles // Use InputFiles rather than InputFile to allow the file not to exist
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val internalPublicTxt: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val localOnlyResourceSymbols: RegularFileProperty

    @get:OutputFile
    abstract val externalPublicTxt: RegularFileProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(WorkAction::class.java) { parameters ->
            parameters.initializeFromAndroidVariantTask(this)
            parameters.internalPublicTxt.set(internalPublicTxt)
            parameters.symbols.set(localOnlyResourceSymbols)
            parameters.externalPublicTxt.set(externalPublicTxt)
        }
    }

    abstract class WorkAction: ProfileAwareWorkAction<WorkAction.Parameters>() {
        abstract class Parameters: ProfileAwareWorkAction.Parameters() {
            abstract val internalPublicTxt: RegularFileProperty
            abstract val symbols: RegularFileProperty
            abstract val externalPublicTxt: RegularFileProperty
        }

        override fun run() {
            writeFile(
                internalPublicTxt = parameters.internalPublicTxt.get().asFile.toPath(),
                symbols = parameters.symbols.get().asFile.toPath(),
                externalPublicTxt = parameters.externalPublicTxt.get().asFile.toPath()
            )
        }
    }

    class CreationAction(creationConfig: ComponentCreationConfig):
        VariantTaskCreationAction<GenerateApiPublicTxtTask, ComponentCreationConfig>(
            creationConfig
        ) {

        override val name: String = computeTaskName("generate", "ExternalPublicTxt")
        override val type: Class<GenerateApiPublicTxtTask> get() = GenerateApiPublicTxtTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<GenerateApiPublicTxtTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(taskProvider,
            GenerateApiPublicTxtTask::externalPublicTxt)
                .withName("public.txt")
                .on(SingleArtifact.PUBLIC_ANDROID_RESOURCES_LIST)
        }

        override fun configure(task: GenerateApiPublicTxtTask) {
            super.configure(task)
            task.internalPublicTxt.setDisallowChanges(creationConfig.artifacts.get(InternalArtifactType.PUBLIC_RES))
            task.localOnlyResourceSymbols.setDisallowChanges(creationConfig.artifacts.get(InternalArtifactType.LOCAL_ONLY_SYMBOL_LIST))
        }
    }

    companion object {
        @VisibleForTesting
        internal fun writeFile(
            internalPublicTxt: Path,
            symbols: Path,
            externalPublicTxt: Path
        ) {
            if (Files.exists(internalPublicTxt)) {
                FileUtils.copyFile(
                    internalPublicTxt,
                    externalPublicTxt
                )
            } else {
                Files.newBufferedWriter(externalPublicTxt).use { writer ->
                    writePublicTxtFile(SymbolIo.readRDef(symbols), writer)
                }
            }
        }
    }
}
