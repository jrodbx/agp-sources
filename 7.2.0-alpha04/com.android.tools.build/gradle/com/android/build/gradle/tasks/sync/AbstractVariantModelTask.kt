/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.tasks.sync

import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.ide.model.sync.Variant
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.BufferedOutputStream
import java.io.FileOutputStream

@DisableCachingByDefault
abstract class AbstractVariantModelTask: NonIncrementalTask() {

    companion object {
        fun getTaskName(creationConfig: ComponentCreationConfig) =
            creationConfig.computeTaskName("create", "VariantModel")
    }

    @get:OutputFile
    abstract val outputModelFile: RegularFileProperty

    override fun doTaskAction() {
        val variant = Variant.newBuilder().also { variant ->
            addVariantContent(variant)
        }.build()

        BufferedOutputStream(FileOutputStream(outputModelFile.asFile.get())).use {
            variant.writeTo(it)
        }
    }

    protected abstract fun addVariantContent(variant: Variant.Builder)

    abstract class CreationAction<T : AbstractVariantModelTask, U: ComponentCreationConfig>(
        creationConfig: U
    ): VariantTaskCreationAction<T, U>(
            creationConfig = creationConfig,
            dependsOnPreBuildTask = false,
        ) {

        override val name: String
            get() = getTaskName(creationConfig)

        override fun handleProvider(taskProvider: TaskProvider<T>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                AbstractVariantModelTask::outputModelFile
            ).on(InternalArtifactType.VARIANT_MODEL)
        }
    }
}
