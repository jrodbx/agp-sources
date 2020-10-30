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

import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.signing.SigningConfigData
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider

/**
 * Task that writes the SigningConfig information to a file.
 */
@CacheableTask
abstract class SigningConfigWriterTask : NonIncrementalTask() {

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val validatedSigningOutput: DirectoryProperty

    @get:Nested
    @get:Optional
    var signingConfigData: SigningConfigData? = null
        internal set

    // Add the store file path as an input as SigningConfigData ignores it (see its javadoc). This
    // will break cache relocatability, but we have to accept it for correctness (see bug
    // 135509623#comment6).
    @get:Input
    @get:Optional
    var storeFilePath: String? = null
        internal set

    public override fun doTaskAction() {
        SigningConfigUtils.save(outputDirectory.get().asFile, signingConfigData)
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<SigningConfigWriterTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("signingConfigWriter")

        override val type: Class<SigningConfigWriterTask>
            get() = SigningConfigWriterTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out SigningConfigWriterTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesDir(
                InternalArtifactType.SIGNING_CONFIG,
                taskProvider,
                SigningConfigWriterTask::outputDirectory
            )
        }

        override fun configure(task: SigningConfigWriterTask) {
            super.configure(task)

            variantScope.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.VALIDATE_SIGNING_CONFIG,
                task.validatedSigningOutput
            )

            task.signingConfigData = variantScope.variantDslInfo.signingConfig?.let {
                task.storeFilePath = it.storeFile?.path
                SigningConfigData.fromSigningConfig(it)
            }
        }
    }
}