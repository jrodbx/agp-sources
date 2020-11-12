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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.ProguardFiles
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider

abstract class ExtractProguardFiles : NonIncrementalGlobalTask() {

    @get:Input
    abstract val enableKeepRClass: Property<Boolean>

    @get:Internal("only for task execution")
    abstract val buildDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val proguardFilesDir: DirectoryProperty

    override fun doTaskAction() {
        for (name in ProguardFiles.KNOWN_FILE_NAMES) {
            val defaultProguardFile = ProguardFiles.getDefaultProguardFile(name, buildDirectory)
            if (!defaultProguardFile.isFile) {
                ProguardFiles.createProguardFile(name, defaultProguardFile, enableKeepRClass.get())
            }
        }
    }

    class CreationAction(
        private val projectOptions: ProjectOptions,
        globalScope: GlobalScope,
        private val projectInfo: ProjectInfo
    ) : GlobalTaskCreationAction<ExtractProguardFiles>(globalScope) {

        override val name = "extractProguardFiles"
        override val type = ExtractProguardFiles::class.java

        override fun configure(task: ExtractProguardFiles) {
            super.configure(task)

            task.enableKeepRClass.set(
                !projectOptions.get(BooleanOption.ENABLE_R_TXT_RESOURCE_SHRINKING)
            )
            task.buildDirectory.set(task.project.layout.buildDirectory)
            task.outputs.doNotCacheIf(
                "This task is fast-running, so the cacheability overhead could outweigh its benefit"
            ) { true }
        }

        override fun handleProvider(provider: TaskProvider<ExtractProguardFiles>) {
            super.handleProvider(provider)

            globalScope.globalArtifacts
                .setInitialProvider(provider, ExtractProguardFiles::proguardFilesDir)
                .atLocation(
                    ProguardFiles
                        .getDefaultProguardFileDir(projectInfo.getProject().layout.buildDirectory)
                        .absolutePath)
                .on(InternalArtifactType.DEFAULT_PROGUARD_FILES)
        }
    }
}
